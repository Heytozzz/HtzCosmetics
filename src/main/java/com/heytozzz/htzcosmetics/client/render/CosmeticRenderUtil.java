package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

/** Shared drawing helpers for cosmetics outside the normal player render layer (menu thumbnails and preview). */
public final class CosmeticRenderUtil {
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

    /** Draws the cosmetic alone with its idle animation at {@code seconds}. */
    public static void drawIdle(BakedCosmetic c, PoseStack pose, GeometrySink sink, float seconds, int light, int overlay) {
        sink.draw(pose, renderType(c), (p, vertices) -> {
            CosmeticAnimator.poseAt(c, seconds, AnimationClip.Trigger.IDLE);
            c.root().render(p, vertices, light, overlay);
        });
    }

    /** Draws the cosmetic attached to its body part of a posed {@code model} (idle animation). */
    public static void drawOnModel(PlayerModel model, BakedCosmetic c, PoseStack pose, GeometrySink sink,
                                   float seconds, int light, int overlay) {
        pose.pushPose();
        AnchorTransform.apply(pose, model.root(), anchorPart(model, c.definition().anchor()));
        drawIdle(c, pose, sink, seconds, light, overlay);
        pose.popPose();
    }

    static RenderType renderType(BakedCosmetic c) {
        return c.translucent() ? translucent(c.texture()) : cutout(c.texture());
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
}
