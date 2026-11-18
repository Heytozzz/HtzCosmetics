package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Shared drawing helpers for cosmetics outside the player (menu thumbnails). */
public final class CosmeticRenderUtil {
    private CosmeticRenderUtil() {
    }

    /** Draws the cosmetic with its idle animation at {@code seconds}. */
    public static void drawIdle(BakedCosmetic c, PoseStack pose, MultiBufferSource buffers, float seconds,
                                int light, int overlay) {
        CosmeticAnimator.poseAt(c, seconds, AnimationClip.Trigger.IDLE);
        RenderType type = c.translucent() ? RenderType.entityTranslucent(c.texture()) : RenderType.entityCutoutNoCull(c.texture());
        c.root().render(pose, buffers.getBuffer(type), light, overlay);
    }
}
