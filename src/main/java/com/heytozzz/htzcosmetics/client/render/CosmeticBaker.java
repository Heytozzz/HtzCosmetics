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

    public static ModelPart bake(CosmeticModel model) {
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
            parts[i] = part;
        }
        Map<String, ModelPart> roots = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (bones.get(i).parentIndex() < 0) roots.put(bones.get(i).name(), parts[i]);
        }
        return new ModelPart(List.of(), roots);
    }

    private static ModelPart.Cube toVanilla(Cube c, int texWidth, int texHeight) {
        float grow = (float) c.inflate();
        return new ModelPart.Cube(c.u(), c.v(),
            (float) c.origin()[0], (float) c.origin()[1], (float) c.origin()[2],
            (float) c.size()[0], (float) c.size()[1], (float) c.size()[2],
            grow, grow, grow, c.mirror(), texWidth, texHeight, ALL_FACES);
    }
}
