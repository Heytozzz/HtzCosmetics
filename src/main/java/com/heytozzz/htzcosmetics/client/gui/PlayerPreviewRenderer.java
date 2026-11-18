package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import com.heytozzz.htzcosmetics.client.render.CosmeticRenderUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws the player model (skin + worn cosmetics) for the wardrobe preview when there is no world, so no player
 * entity exists. Same coordinate setup as {@link ThumbnailsRenderer}.
 */
public final class PlayerPreviewRenderer extends PictureInPictureRenderer<PlayerPreviewRenderState> {
    /** Model pixels shown vertically: from a bit above the head (cosmetics) down to the feet. */
    private static final float TOP = -21.0F;
    private static final float BOTTOM = 24.5F;

    public PlayerPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<PlayerPreviewRenderState> getRenderStateClass() {
        return PlayerPreviewRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected void renderToTexture(PlayerPreviewRenderState state, PoseStack pose) {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        float height = state.y1() - state.y0();
        float width = state.x1() - state.x0();
        float k = Math.min(height * 0.92F / (BOTTOM - TOP), width * 0.9F / 16.0F); // GUI pixels per model pixel

        pose.pushPose();
        pose.scale(k * 16.0F, k * 16.0F, -k * 16.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F + state.yawDegrees()));
        pose.translate(0.0F, -(TOP + BOTTOM) / 2.0F / 16.0F, 0.0F);

        state.model().resetPose();
        int light = LightTexture.FULL_BRIGHT;
        state.model().renderToBuffer(pose, bufferSource.getBuffer(RenderType.entityTranslucent(state.skin())), light,
            OverlayTexture.NO_OVERLAY);
        for (BakedCosmetic c : state.cosmetics()) {
            CosmeticRenderUtil.drawOnModel(state.model(), c, pose, bufferSource, state.seconds(), light,
                OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        bufferSource.endBatch();
    }

    @Override
    protected String getTextureLabel() {
        return "htzcosmetics player preview";
    }
}
