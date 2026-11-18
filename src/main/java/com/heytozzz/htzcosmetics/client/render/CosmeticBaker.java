package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.model.Bone;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.Cube;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a validated {@link CosmeticModel} into vanilla {@link ModelPart}s, so cosmetics are drawn by the same code
 * path as vanilla entity models (which is what Sodium and Iris expect).
 */
public final class CosmeticBaker {
    private static final Set<Direction> ALL_FACES = EnumSet.allOf(Direction.class);
    /** Bloom is drawn as this many growing, fading copies of the model. */
    public static final int BLOOM_LAYERS = 3;

    private CosmeticBaker() {
    }

    /** One body part's share of a cosmetic: the root holding every top-level bone that follows {@code anchor}. */
    public record Piece(Anchor anchor, ModelPart root) {
    }

    /** A complete copy of the model tree (pieces plus each bone's part, same order as the model's bones). */
    public record Tree(List<Piece> pieces, ModelPart[] bones) {
    }

    /**
     * Result of baking: the model, its bloom copies (each cube grown a bit more), and the approximate bounding box
     * in model pixels (used to frame menu thumbnails), with every piece at its body part's rest position.
     */
    public record Baked(Tree main, List<Tree> bloom, float[] center, float size) {
    }

    /**
     * @param anchor      the cosmetic's own anchor (for top-level bones that name none)
     * @param bloomRadius how far the outermost bloom copy grows, in model pixels; 0 = no bloom copies
     */
    public static Baked bake(CosmeticModel model, Anchor anchor, double bloomRadius) {
        Tree main = tree(model, anchor, 0);
        List<Tree> bloom = new ArrayList<>();
        if (bloomRadius > 0) {
            for (int k = 1; k <= BLOOM_LAYERS; k++) bloom.add(tree(model, anchor, (float) (bloomRadius * k / BLOOM_LAYERS)));
        }
        return new Baked(main, List.copyOf(bloom), bounds(model, anchor, 0), bounds(model, anchor, 1)[0]);
    }

    private static Tree tree(CosmeticModel model, Anchor anchor, float grow) {
        List<Bone> bones = model.bones();
        int n = bones.size();
        ModelPart[] parts = new ModelPart[n];
        // Parents always come before children, so building in reverse order has every child ready first.
        for (int i = n - 1; i >= 0; i--) {
            Bone bone = bones.get(i);
            Map<String, ModelPart> children = new HashMap<>();
            for (int j = i + 1; j < n; j++) {
                if (bones.get(j).parentIndex() == i) children.put(bones.get(j).name(), parts[j]);
            }
            List<ModelPart.Cube> cubes = new ArrayList<>();
            for (Cube c : bone.cubes()) cubes.add(toVanilla(c, model.textureWidth(), model.textureHeight(), grow));

            ModelPart part = new ModelPart(cubes, children);
            part.setPos((float) bone.pivot()[0], (float) bone.pivot()[1], (float) bone.pivot()[2]);
            part.setRotation((float) Math.toRadians(bone.rotation()[0]), (float) Math.toRadians(bone.rotation()[1]),
                (float) Math.toRadians(bone.rotation()[2]));
            part.setInitialPose(part.storePose()); // rest pose, restored before every animated frame
            parts[i] = part;
        }
        Map<Anchor, Map<String, ModelPart>> roots = new EnumMap<>(Anchor.class);
        for (int i = 0; i < n; i++) {
            Bone b = bones.get(i);
            if (b.parentIndex() >= 0) continue;
            roots.computeIfAbsent(b.anchor() != null ? b.anchor() : anchor, a -> new HashMap<>()).put(b.name(), parts[i]);
        }
        List<Piece> pieces = new ArrayList<>();
        roots.forEach((a, r) -> pieces.add(new Piece(a, new ModelPart(List.of(), r))));
        return new Tree(List.copyOf(pieces), parts);
    }

    /**
     * Rest position of a body part relative to another, in model pixels (vanilla player model layout).
     * Used to lay out multi-part cosmetics where no player is drawn (thumbnails).
     */
    public static float[] restOffset(Anchor part, Anchor from) {
        float[] a = restPivot(part);
        float[] b = restPivot(from);
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] restPivot(Anchor anchor) {
        return switch (anchor) {
            case HEAD, BODY -> new float[]{0, 0, 0};
            case RIGHT_ARM -> new float[]{-5, 2, 0};
            case LEFT_ARM -> new float[]{5, 2, 0};
            case RIGHT_LEG -> new float[]{-1.9F, 12, 0};
            case LEFT_LEG -> new float[]{1.9F, 12, 0};
        };
    }

    /** mode 0: center (x, y, z); mode 1: {largest side}. Bone rotations are ignored (good enough for framing). */
    private static float[] bounds(CosmeticModel model, Anchor anchor, int mode) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        List<Bone> bones = model.bones();
        float[][] offset = new float[bones.size()][3];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            float[] base = b.parentIndex() >= 0 ? offset[b.parentIndex()]
                : restOffset(b.anchor() != null ? b.anchor() : anchor, anchor);
            for (int a = 0; a < 3; a++) offset[i][a] = (float) b.pivot()[a] + base[a];
            for (Cube c : b.cubes()) {
                for (int a = 0; a < 3; a++) {
                    min[a] = Math.min(min[a], offset[i][a] + (float) (c.origin()[a] - c.inflate()));
                    max[a] = Math.max(max[a], offset[i][a] + (float) (c.origin()[a] + c.size()[a] + c.inflate()));
                }
            }
        }
        if (min[0] > max[0]) return mode == 0 ? new float[3] : new float[]{16};
        if (mode == 0) return new float[]{(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2};
        return new float[]{Math.max(1, Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])))};
    }

    private static ModelPart.Cube toVanilla(Cube c, int texWidth, int texHeight, float extraGrow) {
        float grow = (float) c.inflate() + extraGrow;
        return new ModelPart.Cube(c.u(), c.v(),
            (float) c.origin()[0], (float) c.origin()[1], (float) c.origin()[2],
            (float) c.size()[0], (float) c.size()[1], (float) c.size()[2],
            grow, grow, grow, c.mirror(), texWidth, texHeight, ALL_FACES);
    }
}
