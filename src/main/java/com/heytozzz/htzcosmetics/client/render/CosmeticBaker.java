package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.model.Bone;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.Cube;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.util.ArrayList;
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

    private CosmeticBaker() {
    }

    /**
     * Result of baking: the root part plus each bone's part (same order as the model's bones), and the approximate
     * bounding box of the model in model pixels (used to frame menu thumbnails).
     */
    public record Baked(ModelPart root, ModelPart[] bones, float[] center, float size) {
    }

    public static Baked bake(CosmeticModel model) {
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
            for (Cube c : bone.cubes()) cubes.add(toVanilla(c, model.textureWidth(), model.textureHeight()));

            ModelPart part = new ModelPart(cubes, children);
            part.setPos((float) bone.pivot()[0], (float) bone.pivot()[1], (float) bone.pivot()[2]);
            part.setRotation((float) Math.toRadians(bone.rotation()[0]), (float) Math.toRadians(bone.rotation()[1]),
                (float) Math.toRadians(bone.rotation()[2]));
            part.setInitialPose(part.storePose()); // rest pose, restored before every animated frame
            parts[i] = part;
        }
        Map<String, ModelPart> roots = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (bones.get(i).parentIndex() < 0) roots.put(bones.get(i).name(), parts[i]);
        }
        return new Baked(new ModelPart(List.of(), roots), parts, bounds(model, 0), bounds(model, 1)[0]);
    }

    /** mode 0: center (x, y, z); mode 1: {largest side}. Bone rotations are ignored (good enough for framing). */
    private static float[] bounds(CosmeticModel model, int mode) {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        List<Bone> bones = model.bones();
        float[][] offset = new float[bones.size()][3];
        for (int i = 0; i < bones.size(); i++) {
            Bone b = bones.get(i);
            for (int a = 0; a < 3; a++) {
                offset[i][a] = (float) b.pivot()[a] + (b.parentIndex() >= 0 ? offset[b.parentIndex()][a] : 0);
            }
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

    private static ModelPart.Cube toVanilla(Cube c, int texWidth, int texHeight) {
        float grow = (float) c.inflate();
        return new ModelPart.Cube(c.u(), c.v(),
            (float) c.origin()[0], (float) c.origin()[1], (float) c.origin()[2],
            (float) c.size()[0], (float) c.size()[1], (float) c.size()[2],
            grow, grow, grow, c.mirror(), texWidth, texHeight, ALL_FACES);
    }
}
