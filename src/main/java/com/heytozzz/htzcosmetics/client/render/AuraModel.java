package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Boxes shaped like the player's body parts, used to draw auras on and around the body. They are built from the vanilla
 * player model's box sizes (no textures needed) and hung on the real, posed body parts when drawn, so they follow
 * every movement, including animation mods.
 */
public final class AuraModel {
    /** Gap between the skin's outer layer and the aura, in model pixels. */
    private static final float GAP = 0.1F;
    private static final AuraModel WIDE = new AuraModel(false);
    private static final AuraModel SLIM = new AuraModel(true);

    /** One body part's box, in that part's own space (vanilla player model numbers). */
    record Box(float x, float y, float z, float w, float h, float d, float outerLayer) {
        float cx() {
            return x + w / 2;
        }

        float cy() {
            return y + h / 2;
        }

        float cz() {
            return z + d / 2;
        }
    }

    private final Map<Anchor, Box> boxes = new EnumMap<>(Anchor.class);

    private AuraModel(boolean slim) {
        float arm = slim ? 3 : 4;
        boxes.put(Anchor.HEAD, new Box(-4, -8, -4, 8, 8, 8, 0.5F));
        boxes.put(Anchor.BODY, new Box(-4, 0, -2, 8, 12, 4, 0.25F));
        boxes.put(Anchor.RIGHT_ARM, new Box(slim ? -2 : -3, -2, -2, arm, 12, 4, 0.25F));
        boxes.put(Anchor.LEFT_ARM, new Box(-1, -2, -2, arm, 12, 4, 0.25F));
        boxes.put(Anchor.RIGHT_LEG, new Box(-2, 0, -2, 4, 12, 4, 0.25F));
        boxes.put(Anchor.LEFT_LEG, new Box(-2, 0, -2, 4, 12, 4, 0.25F));
    }

    /** The pre-built boxes of one body part: a plain body (thumbnails) and the soft light over the body. */
    record Shells(Box box, ModelPart base, ModelPart fill) {
        /** Half size of the part including the skin's outer layer, in model units, for the glow panels. */
        float[] half() {
            float g = box.outerLayer();
            return new float[]{(box.w() / 2 + g) / 16, (box.h() / 2 + g) / 16, (box.d() / 2 + g) / 16};
        }
    }

    /** Everything an aura draws as geometry, for both arm widths. Built once. */
    public record Geometry(Map<Anchor, Shells> wide, Map<Anchor, Shells> slim) {
        Map<Anchor, Shells> forArms(boolean slimArms) {
            return slimArms ? slim : wide;
        }
    }

    private static final Geometry GEOMETRY = new Geometry(WIDE.shells(), SLIM.shells());

    public static Geometry geometry() {
        return GEOMETRY;
    }

    private Map<Anchor, Shells> shells() {
        Map<Anchor, Shells> out = new EnumMap<>(Anchor.class);
        for (Map.Entry<Anchor, Box> e : boxes.entrySet()) {
            Box b = e.getValue();
            out.put(e.getKey(), new Shells(b, centred(b, -b.outerLayer() - GAP), centred(b, 0)));
        }
        return out;
    }

    static AuraModel get(boolean slim) {
        return slim ? SLIM : WIDE;
    }

    Map<Anchor, Box> boxes() {
        return boxes;
    }

    /**
     * A part with one box centred on its origin, grown by the outer skin layer, the gap and {@code grow}.
     * Centred boxes can be drawn mirrored through their centre (for the outline).
     */
    static ModelPart centred(Box b, float grow) {
        float g = b.outerLayer() + GAP + grow;
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, -b.w() / 2, -b.h() / 2, -b.d() / 2, b.w(), b.h(), b.d(),
            g, g, g, false, 16, 16, EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cube), Map.of());
    }
}
