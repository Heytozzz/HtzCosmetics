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
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws every visible menu thumbnail into one off-screen texture, which Minecraft then places on the menu.
 * Coordinates: the origin is the center of the area, +Y points down (like the screen), 1 unit = 1 GUI pixel.
 */
public final class ThumbnailsRenderer extends PictureInPictureRenderer<ThumbnailsRenderState> {
    private static final float TILT_DEGREES = 20.0F;
    private static final float FILL = 0.62F; // share of the thumbnail square the model occupies

    public ThumbnailsRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<ThumbnailsRenderState> getRenderStateClass() {
        return ThumbnailsRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F; // origin in the middle instead of at the bottom
    }

    @Override
    protected void renderToTexture(ThumbnailsRenderState state, PoseStack pose) {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        float midX = (state.x0() + state.x1()) / 2.0F;
        float midY = (state.y0() + state.y1()) / 2.0F;
        for (ThumbnailsRenderState.Thumb thumb : state.thumbs()) {
            BakedCosmetic c = thumb.cosmetic();
            float pixelsPerModelPixel = thumb.size() * FILL / c.size();
            pose.pushPose();
            pose.translate(thumb.cx() - midX, thumb.cy() - midY, 0.0F);
            // Model space has +Y down like this texture. The renderer flips Z; flip it back so the model is not mirrored.
            pose.scale(pixelsPerModelPixel * 16.0F, pixelsPerModelPixel * 16.0F, -pixelsPerModelPixel * 16.0F);
            pose.mulPose(Axis.XP.rotationDegrees(-TILT_DEGREES));
            pose.mulPose(Axis.YP.rotationDegrees(thumb.yawDegrees()));
            pose.translate(-c.center()[0] / 16.0F, -c.center()[1] / 16.0F, -c.center()[2] / 16.0F);
            CosmeticRenderUtil.drawIdle(c, pose, bufferSource, state.seconds(), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        bufferSource.endBatch();
    }

    @Override
    protected String getTextureLabel() {
        return "htzcosmetics thumbnails";
    }
}
