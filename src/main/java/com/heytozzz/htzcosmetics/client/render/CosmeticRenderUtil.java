package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Shared drawing helpers for cosmetics outside the normal player render layer (menu thumbnails and preview). */
public final class CosmeticRenderUtil {
    private CosmeticRenderUtil() {
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
    public static void drawIdle(BakedCosmetic c, PoseStack pose, MultiBufferSource buffers, float seconds,
                                int light, int overlay) {
        CosmeticAnimator.poseAt(c, seconds, AnimationClip.Trigger.IDLE);
        c.root().render(pose, buffers.getBuffer(renderType(c)), light, overlay);
    }

    /** Draws the cosmetic attached to its body part of a posed {@code model} (idle animation). */
    public static void drawOnModel(PlayerModel model, BakedCosmetic c, PoseStack pose, MultiBufferSource buffers,
                                   float seconds, int light, int overlay) {
        pose.pushPose();
        AnchorTransform.apply(pose, model.root(), anchorPart(model, c.definition().anchor()));
        drawIdle(c, pose, buffers, seconds, light, overlay);
        pose.popPose();
    }

    static RenderType renderType(BakedCosmetic c) {
        return c.translucent() ? RenderType.entityTranslucent(c.texture()) : RenderType.entityCutoutNoCull(c.texture());
    }
}
