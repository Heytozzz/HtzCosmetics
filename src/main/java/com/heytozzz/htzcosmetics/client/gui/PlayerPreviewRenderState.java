package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Wardrobe preview: the player model with the account's skin and worn cosmetics. */
public record PlayerPreviewRenderState(PlayerModel model, ResourceLocation skin, List<BakedCosmetic> cosmetics,
                                       float yawDegrees, float seconds, float zoom, float panX, float panY,
                                       int x0, int y0, int x1, int y1, ScreenRectangle scissorArea, ScreenRectangle bounds)
    implements PictureInPictureRenderState {

    public static PlayerPreviewRenderState of(PlayerModel model, ResourceLocation skin, List<BakedCosmetic> cosmetics,
                                              float yaw, float seconds, float zoom, float panX, float panY,
                                              int x0, int y0, int x1, int y1, ScreenRectangle scissor) {
        return new PlayerPreviewRenderState(model, skin, List.copyOf(cosmetics), yaw, seconds, zoom, panX, panY,
            x0, y0, x1, y1, scissor,
            PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor));
    }

    @Override
    public float scale() {
        return 1.0F;
    }
}
