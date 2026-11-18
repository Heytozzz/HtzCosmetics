package com.heytozzz.htzcosmetics.client.gui;

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
 * Draws the wardrobe preview: the player model with the account's skin and the worn cosmetics.
 * Same coordinate setup as {@link ThumbnailsRenderer}.
 */
public final class PlayerPreviewRenderer extends HtzPipRenderer<PlayerPreviewRenderState> {
    /** Model pixels shown vertically: from a bit above the head (cosmetics) down to the feet. */
    private static final float TOP = -21.0F;
    private static final float BOTTOM = 24.5F;

    //? if >=26.2 {
    /*public PlayerPreviewRenderer() {
        super();
    }
    *///?} else {
    public PlayerPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }
    //?}

    @Override
    public Class<PlayerPreviewRenderState> getRenderStateClass() {
        return PlayerPreviewRenderState.class;
    }

    @Override
    protected Lighting.Entry lighting() {
        return Lighting.Entry.PLAYER_SKIN; // same light as the vanilla skin customisation preview
    }

    @Override
    protected void draw(PlayerPreviewRenderState state, PoseStack pose, Drawer drawer) {
        float height = state.y1() - state.y0();
        float width = state.x1() - state.x0();
        float k = Math.min(height * 0.92F / (BOTTOM - TOP), width * 0.9F / 16.0F) * state.zoom(); // GUI px per model px

        pose.pushPose();
        pose.translate(state.panX(), state.panY(), 0.0F);
        // A plain (non-mirrored) scale: a negative axis here would show the player mirrored, left and right swapped,
        // and turn the lighting around.
        pose.scale(k * 16.0F, k * 16.0F, k * 16.0F);
        pose.mulPose(Axis.XP.rotationDegrees(-state.pitchDegrees())); // tilt towards / away from the viewer
        pose.mulPose(Axis.YP.rotationDegrees(state.yawDegrees()));
        pose.translate(0.0F, -(TOP + BOTTOM) / 2.0F / 16.0F, 0.0F);

        var model = state.model();
        int light = LightTexture.FULL_BRIGHT;
        drawer.draw(pose, CosmeticRenderUtil.translucent(state.skin()), (p, vertices) -> {
            model.resetPose();
            model.root().render(p, vertices, light, OverlayTexture.NO_OVERLAY);
        });
        model.resetPose(); // cosmetic anchors read the rest pose
        CosmeticRenderUtil.drawOnModel(model, state.cosmetics(), pose, drawer::draw, state.seconds(), light,
            OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override
    protected String getTextureLabel() {
        return "htzcosmetics player preview";
    }
}
