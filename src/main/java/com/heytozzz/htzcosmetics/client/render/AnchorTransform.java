package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.mixin.ModelPartAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Moves the pose stack onto a body part so cosmetics follow it, including when animation mods are used.
 *
 * <p>Vanilla: the part itself holds the boxes, so its own transform is enough.
 *
 * <p>Animation mods such as Entity Model Features (Fresh Animations) replace the visible geometry with CHILD parts
 * hung under the vanilla part and animate those children (and sometimes the model root). In that case we walk down
 * to the child that actually draws the part and apply how far each link has moved from its rest pose
 * ({@code animated * rest^-1}). At rest this adds nothing, so cosmetics keep their normal placement; while animated
 * they move exactly like the visible part. No dependency on any animation mod.
 */
final class AnchorTransform {
    private static final int MAX_DEPTH = 8;
    // Render thread only: scratch objects reused every frame.
    private static final ModelPart SCRATCH = new ModelPart(List.of(), Map.of());
    private static final PoseStack SCRATCH_STACK = new PoseStack();
    private static final Matrix4f REST = new Matrix4f();
    private static final Matrix4f DELTA = new Matrix4f();

    private AnchorTransform() {
    }

    static void apply(PoseStack pose, ModelPart root, ModelPart anchor) {
        // Vanilla never moves the root (identity), but animation packs may.
        root.translateAndRotate(pose);
        anchor.translateAndRotate(pose);
        if (hasBoxes(anchor)) return; // vanilla (or attach-style) model: done

        List<ModelPart> chain = new ArrayList<>(4);
        if (!findDrawnChild(anchor, chain, 0)) return;
        for (ModelPart link : chain) applyDelta(pose, link);
    }

    /** Depth-first search for the first visible descendant with boxes; fills {@code chain} with the path to it. */
    private static boolean findDrawnChild(ModelPart part, List<ModelPart> chain, int depth) {
        if (depth >= MAX_DEPTH) return false;
        for (ModelPart child : ((ModelPartAccessor) (Object) part).htzcosmetics$children().values()) {
            if (!child.visible) continue;
            chain.add(child);
            if (hasBoxes(child) || findDrawnChild(child, chain, depth + 1)) return true;
            chain.remove(chain.size() - 1);
        }
        return false;
    }

    private static boolean hasBoxes(ModelPart part) {
        return !((ModelPartAccessor) (Object) part).htzcosmetics$cubes().isEmpty();
    }

    /** Applies {@code animatedTransform * restTransform^-1} of one part. */
    private static void applyDelta(PoseStack pose, ModelPart part) {
        // Both matrices are computed on the same plain vanilla part, from the rest pose and the current pose.
        SCRATCH.loadPose(part.getInitialPose());
        REST.set(localMatrix(SCRATCH));
        SCRATCH.loadPose(part.storePose());
        Matrix4f animated = localMatrix(SCRATCH);
        if (animated.equals(REST, 1e-5f)) return;
        DELTA.set(animated).mul(REST.invert());
        pose.mulPose(DELTA);
    }

    private static Matrix4f localMatrix(ModelPart part) {
        SCRATCH_STACK.setIdentity();
        part.translateAndRotate(SCRATCH_STACK);
        return new Matrix4f(SCRATCH_STACK.last().pose());
    }
}
