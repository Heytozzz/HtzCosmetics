package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.manifest.Aura;
import com.heytozzz.htzcosmetics.core.manifest.Glow;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import com.heytozzz.htzcosmetics.mixin.ModelPartAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Draws cosmetics: on players in the world, on the wardrobe preview and on menu thumbnails.
 *
 * <p>Glow: an emissive cosmetic is drawn at full brightness with an emissive render type (shader packs such as
 * Complementary also give it their own bloom). Bloom is drawn as a few slightly bigger copies of the model in the
 * glow colour, see-through and without depth writes, so it works without shaders too. Auras draw a smooth glow
 * behind each body part ({@link AuraGlow}), like the vanilla glowing effect but never through walls.
 */
public final class CosmeticRenderUtil {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID, "textures/misc/white.png");
    /**
     * Brightness of each bloom layer, from the innermost (strongest) outwards. Layers add up (front and back faces
     * too), so these stay low: at "bloom": 1 the brightest spot reaches about the glow colour, not white.
     */
    private static final float[] BLOOM_FALLOFF = {0.38F, 0.22F, 0.11F};
    /** The aura's fill is drawn on both sides of each box, so each side gets half. */
    private static final float FILL_FACTOR = 0.3F;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final int SILHOUETTE = 0xFF2A2D3A;
    private static RenderType additive;
    private static final ResourceLocation GLOW_SOFT = ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID, "textures/misc/glow_soft.png");
    private static final ResourceLocation GLOW_RIM = ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID, "textures/misc/glow_rim.png");
    private static RenderType glowSoft;
    private static RenderType glowRim;

    private CosmeticRenderUtil() {
    }

    /**
     * Where geometry goes: drawn now or submitted for later, depending on the Minecraft version.
     * The {@code body} may run later, so it poses the model itself right before drawing.
     */
    @FunctionalInterface
    public interface GeometrySink {
        void draw(PoseStack pose, RenderType type, BiConsumer<PoseStack, VertexConsumer> body);
    }

    /** Moves {@code pose} onto a body part; returns false if that part is hidden (then nothing is drawn on it). */
    @FunctionalInterface
    public interface AnchorPoser {
        boolean apply(PoseStack pose, Anchor anchor);
    }

    /** The body part of {@code model} a cosmetic is attached to. */
    public static ModelPart anchorPart(PlayerModel model, Anchor anchor) {
        return switch (anchor) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }

    /** Follows the posed parts of {@code model}, including what animation mods do to them. */
    public static AnchorPoser onModel(PlayerModel model) {
        return (pose, anchor) -> {
            ModelPart part = anchorPart(model, anchor);
            if (!part.visible) return false;
            AnchorTransform.apply(pose, model.root(), part);
            return true;
        };
    }

    /** True if the model has slim (3 pixel) arms; animation mods that hide the vanilla box count as wide. */
    public static boolean slimArms(PlayerModel model) {
        List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) model.rightArm).htzcosmetics$cubes();
        return !cubes.isEmpty() && cubes.get(0).maxX - cubes.get(0).minX < 3.5F;
    }

    /**
     * Draws one cosmetic: its pieces (each on its own body part), its glow, and its aura.
     *
     * @param gui        true in menus (orthographic view), false in the world
     * @param silhouette draw a plain body under an aura (thumbnails, where no player is drawn)
     */
    public static void draw(BakedCosmetic c, Integer color, PoseStack pose, AnchorPoser poser, GeometrySink sink,
                            float seconds, AnimationClip.Trigger trigger, int light, int overlay, boolean slim,
                            boolean gui, boolean silhouette) {
        Glow glow = TintCache.glow(c, color);
        if (c.main() != null) drawModel(c, TintCache.texture(c, color), glow, pose, poser, sink, seconds, trigger, light, overlay);
        if (c.aura() != null && glow != null) {
            drawAura(c.aura().forArms(slim), c.definition().aura(), glow, pose, poser, sink, seconds, gui, silhouette);
        }
    }

    /** One worn cosmetic and the colour its wearer chose (null = default). */
    public record Worn(BakedCosmetic cosmetic, Integer color) {
    }

    /**
     * Draws everything one player wears. An aura also glows around the other worn cosmetics (hats, wings...), so
     * the whole outfit shines, not only the body.
     */
    public static void drawWorn(List<Worn> worn, PoseStack pose, AnchorPoser poser, GeometrySink sink, float seconds,
                                AnimationClip.Trigger trigger, int light, int overlay, boolean slim, boolean gui) {
        for (Worn w : worn) {
            draw(w.cosmetic(), w.color(), pose, poser, sink, seconds, trigger, light, overlay, slim, gui, false);
        }
        for (Worn aura : worn) {
            if (aura.cosmetic().aura() == null) continue;
            Glow glow = TintCache.glow(aura.cosmetic(), aura.color());
            if (glow == null) continue;
            for (Worn other : worn) {
                if (other.cosmetic().main() != null) {
                    glowAround(other.cosmetic(), aura.cosmetic().definition().aura(), glow, pose, poser, sink, seconds, gui);
                }
            }
        }
    }

    /** The aura's glow panels behind each piece of another cosmetic (its box at rest). */
    private static void glowAround(BakedCosmetic c, Aura aura, Glow glow, PoseStack pose, AnchorPoser poser,
                                   GeometrySink sink, float seconds, boolean gui) {
        int bloom = glowColor(glow, seconds, glow.bloom());
        int rim = glowColor(glow, seconds, aura.outlineStrength());
        float bloomWidth = (float) glow.radius() / 16.0F;
        float rimWidth = (float) aura.outline() / 16.0F;
        for (CosmeticBaker.Piece piece : c.main().pieces()) {
            if (piece.half()[0] <= 0 && piece.half()[1] <= 0) continue;
            float[] half = {piece.half()[0] / 16.0F, piece.half()[1] / 16.0F, piece.half()[2] / 16.0F};
            pose.pushPose();
            if (poser.apply(pose, piece.anchor())) {
                pose.translate(piece.center()[0] / 16.0F, piece.center()[1] / 16.0F, piece.center()[2] / 16.0F);
                if (glow.bloom() > 0) {
                    sink.draw(pose, glowPanel(GLOW_SOFT), (p, v) -> AuraGlow.write(p.last(), v, half, bloomWidth, gui, bloom));
                }
                if (aura.outline() > 0 && aura.outlineStrength() > 0) {
                    sink.draw(pose, glowPanel(GLOW_RIM), (p, v) -> AuraGlow.write(p.last(), v, half, rimWidth, gui, rim));
                }
            }
            pose.popPose();
        }
    }

    private static void drawModel(BakedCosmetic c, ResourceLocation texture, Glow glow, PoseStack pose,
                                  AnchorPoser poser, GeometrySink sink, float seconds, AnimationClip.Trigger trigger,
                                  int light, int overlay) {
        boolean emissive = glow != null && glow.emissive();
        RenderType type = emissive ? emissive(texture) : c.translucent() ? translucent(texture) : cutout(texture);
        int mainLight = emissive ? FULL_BRIGHT : light;
        List<CosmeticBaker.Piece> pieces = c.main().pieces();
        for (int i = 0; i < pieces.size(); i++) {
            CosmeticBaker.Piece piece = pieces.get(i);
            pose.pushPose();
            if (poser.apply(pose, piece.anchor())) {
                ModelPart root = piece.root();
                sink.draw(pose, type, (p, vertices) -> {
                    CosmeticAnimator.poseAt(c, seconds, trigger);
                    root.render(p, vertices, mainLight, overlay);
                });
                for (int k = 0; k < c.bloom().size(); k++) {
                    CosmeticBaker.Tree layer = c.bloom().get(k);
                    ModelPart layerRoot = layer.pieces().get(i).root();
                    int color = glowColor(glow, seconds, glow.bloom() * BLOOM_FALLOFF[k]);
                    sink.draw(pose, additive(), (p, vertices) -> {
                        CosmeticAnimator.poseAt(c, seconds, trigger);
                        CosmeticAnimator.copyPose(c.bones(), layer.bones());
                        layerRoot.render(p, vertices, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
                    });
                }
            }
            pose.popPose();
        }
    }

    private static void drawAura(Map<Anchor, AuraModel.Shells> shells, Aura aura, Glow glow, PoseStack pose,
                                 AnchorPoser poser, GeometrySink sink, float seconds, boolean gui, boolean silhouette) {
        int fill = glowColor(glow, seconds, aura.fill() * FILL_FACTOR);
        int bloom = glowColor(glow, seconds, glow.bloom());
        int rim = glowColor(glow, seconds, aura.outlineStrength());
        float bloomWidth = (float) glow.radius() / 16.0F;
        float rimWidth = (float) aura.outline() / 16.0F;
        for (Anchor anchor : Anchor.values()) {
            AuraModel.Shells s = shells.get(anchor);
            pose.pushPose();
            if (poser.apply(pose, anchor)) {
                AuraModel.Box b = s.box();
                pose.translate(b.cx() / 16.0F, b.cy() / 16.0F, b.cz() / 16.0F);
                if (silhouette) drawPart(sink, pose, cutout(WHITE), s.base(), SILHOUETTE);
                if (aura.fill() > 0) drawPart(sink, pose, additive(), s.fill(), fill);
                float[] half = s.half();
                // Smooth glow behind the part: the wide bloom, then the brighter edge on top of it.
                if (glow.bloom() > 0) {
                    sink.draw(pose, glowPanel(GLOW_SOFT), (p, v) -> AuraGlow.write(p.last(), v, half, bloomWidth, gui, bloom));
                }
                if (aura.outline() > 0 && aura.outlineStrength() > 0) {
                    sink.draw(pose, glowPanel(GLOW_RIM), (p, v) -> AuraGlow.write(p.last(), v, half, rimWidth, gui, rim));
                }
            }
            pose.popPose();
        }
    }

    private static void drawPart(GeometrySink sink, PoseStack pose, RenderType type, ModelPart part, int color) {
        sink.draw(pose, type, (p, vertices) -> part.render(p, vertices, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color));
    }

    /** Glow colour at {@code seconds}, with the breathing pulse and {@code strength} as its opacity. */
    private static int glowColor(Glow glow, float seconds, double strength) {
        double f = Math.max(0, Math.min(1, strength * glow.pulseAt(seconds)));
        return (int) Math.round(f * 255) << 24 | glow.colorAt(seconds);
    }

    /** Draws the cosmetic alone with its idle animation at {@code seconds}, pieces laid out like on a player. */
    public static void drawIdle(BakedCosmetic c, Integer color, PoseStack pose, GeometrySink sink, float seconds,
                                int light, int overlay) {
        var anchor = c.definition().anchor();
        draw(c, color, pose, (p, part) -> {
            float[] o = CosmeticBaker.restOffset(part, anchor);
            p.translate(o[0] / 16.0F, o[1] / 16.0F, o[2] / 16.0F);
            return true;
        }, sink, seconds, AnimationClip.Trigger.IDLE, light, overlay, false, true, true);
    }

    /** Draws everything worn on the body parts of a posed {@code model} in a menu (idle animation). */
    public static void drawOnModel(PlayerModel model, List<Worn> worn, PoseStack pose, GeometrySink sink,
                                   float seconds, int light, int overlay) {
        drawWorn(worn, pose, onModel(model), sink, seconds, AnimationClip.Trigger.IDLE, light, overlay, slimArms(model), true);
    }

    /** Entity render type with smooth transparency (skins, translucent cosmetics). */
    public static RenderType translucent(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(texture);
        *///?} else {
        return RenderType.entityTranslucent(texture);
        //?}
    }

    /** Entity render type with on/off transparency, both faces drawn. */
    public static RenderType cutout(ResourceLocation texture) {
        //? if >=26.1 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
        *///?} else if >=1.21.11 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture);
        *///?} else {
        return RenderType.entityCutoutNoCull(texture);
        //?}
    }

    /** Full brightness, recognised as emissive by shader packs. */
    private static RenderType emissive(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture);
        *///?} else {
        return RenderType.entityTranslucentEmissive(texture);
        //?}
    }

    /**
     * See-through light at full brightness for bloom and aura light. It must NOT write depth: these layers surround
     * other geometry (the cosmetic, clothing, the body), and with depth writes whatever happened to be drawn after
     * them would be hidden inside.
     */
    private static RenderType additive() {
        if (additive == null) {
            //? if >=1.21.11 {
            /*additive = net.minecraft.client.renderer.rendertype.RenderTypes.eyes(WHITE);
            *///?} else {
            additive = RenderType.eyes(WHITE);
            //?}
        }
        return additive;
    }

    /** See-through, full brightness, no depth writes, textured with a soft glow (for the aura's glow panels). */
    private static RenderType glowPanel(ResourceLocation texture) {
        boolean soft = texture == GLOW_SOFT;
        RenderType cached = soft ? glowSoft : glowRim;
        if (cached == null) {
            //? if >=1.21.11 {
            /*cached = net.minecraft.client.renderer.rendertype.RenderTypes.eyes(texture);
            *///?} else {
            cached = RenderType.eyes(texture);
            //?}
            if (soft) glowSoft = cached;
            else glowRim = cached;
        }
        return cached;
    }
}
