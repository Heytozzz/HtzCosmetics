package com.heytozzz.htzcosmetics.client.gui;

import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;

import java.util.List;

/**
 * All cosmetic thumbnails visible in the menu, drawn in ONE 3D pass (Minecraft keeps a single renderer per kind).
 *
 * @param thumbs thumbnails with their centers in screen (GUI) coordinates
 */
public record ThumbnailsRenderState(List<Thumb> thumbs, float seconds, int x0, int y0, int x1, int y1,
                                    ScreenRectangle scissorArea, ScreenRectangle bounds)
    implements PictureInPictureRenderState {

    /** One cosmetic in {@code color} (null = default) drawn centered at (cx, cy), fitted into {@code size} GUI pixels. */
    public record Thumb(BakedCosmetic cosmetic, Integer color, int cx, int cy, int size, float yawDegrees) {
    }

    public static ThumbnailsRenderState of(List<Thumb> thumbs, float seconds, int x0, int y0, int x1, int y1,
                                           ScreenRectangle scissor) {
        return new ThumbnailsRenderState(List.copyOf(thumbs), seconds, x0, y0, x1, y1, scissor,
            PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor));
    }

    @Override
    public float scale() {
        return 1.0F;
    }
}
