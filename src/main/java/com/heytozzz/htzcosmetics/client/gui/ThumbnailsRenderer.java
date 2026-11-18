package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import com.heytozzz.htzcosmetics.client.render.CosmeticRenderUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}

/**
 * Draws every visible menu thumbnail into one off-screen texture, which Minecraft then places on the menu.
 * Coordinates: the origin is the center of the area, +Y points down (like the screen), 1 unit = 1 GUI pixel.
 */
public final class ThumbnailsRenderer extends HtzPipRenderer<ThumbnailsRenderState> {
    private static final float TILT_DEGREES = 20.0F;
    private static final float FILL = 0.62F; // share of the thumbnail square the model occupies

    //? if >=26.2 {
    /*public ThumbnailsRenderer() {
        super();
    }
    *///?} else {
    public ThumbnailsRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }
    //?}

    @Override
    public Class<ThumbnailsRenderState> getRenderStateClass() {
        return ThumbnailsRenderState.class;
    }

    @Override
    protected Lighting.Entry lighting() {
        return Lighting.Entry.ITEMS_3D;
    }

    @Override
    protected void draw(ThumbnailsRenderState state, PoseStack pose, Drawer drawer) {
        float midX = (state.x0() + state.x1()) / 2.0F;
        float midY = (state.y0() + state.y1()) / 2.0F;
        for (ThumbnailsRenderState.Thumb thumb : state.thumbs()) {
            BakedCosmetic c = thumb.cosmetic();
            float pixelsPerModelPixel = thumb.size() * FILL / c.size();
            pose.pushPose();
            pose.translate(thumb.cx() - midX, thumb.cy() - midY, 0.0F);
            // Model space has +Y down like this texture. No negative scale: that would mirror the model.
            pose.scale(pixelsPerModelPixel * 16.0F, pixelsPerModelPixel * 16.0F, pixelsPerModelPixel * 16.0F);
            pose.mulPose(Axis.XP.rotationDegrees(TILT_DEGREES));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F + thumb.yawDegrees()));
            pose.translate(-c.center()[0] / 16.0F, -c.center()[1] / 16.0F, -c.center()[2] / 16.0F);
            CosmeticRenderUtil.drawIdle(c, thumb.color(), pose, drawer::draw, state.seconds(), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    @Override
    protected String getTextureLabel() {
        return "htzcosmetics thumbnails";
    }
}
