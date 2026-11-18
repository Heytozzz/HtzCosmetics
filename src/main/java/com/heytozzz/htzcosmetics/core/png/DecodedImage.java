package com.heytozzz.htzcosmetics.core.png;

/**
 * A decoded texture.
 *
 * @param argb pixels row by row, top-left first, each packed as 0xAARRGGBB
 */
public record DecodedImage(int width, int height, int[] argb) {
    public int pixel(int x, int y) {
        return argb[y * width + x];
    }
}
