package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lets players choose the colour of a cosmetic. The white and grey parts of the texture take the chosen colour
 * (multiplied, so shading stays); coloured parts keep their colours. Glow and aura colours can follow it too.
 *
 * <pre>{@code
 * "tint": {
 *   "default": "#3070FF",                        // colour used until the player picks one
 *   "palette": ["#3070FF", "#FF4040", "#40D060"], // colours offered as squares (up to 16)
 *   "free":    true,                              // also allow any colour (colour wheel)
 *   "glow":    true                               // the glow/aura takes the chosen colour too
 * }
 * }</pre>
 */
public record Tint(int defaultColor, int[] palette, boolean free, boolean glow) {
    public static final int MAX_PALETTE = 16;
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    public static Tint parse(Map<String, Object> obj) throws ValidationException {
        int def = color(Json.string(obj, "default", 7));
        int[] palette = new int[0];
        if (Json.has(obj, "palette")) {
            List<Object> list = Json.array(obj, "palette", MAX_PALETTE);
            palette = new int[list.size()];
            for (int i = 0; i < palette.length; i++) palette[i] = color(Json.asString(list.get(i), "colour", 7));
        }
        boolean free = Json.boolOr(obj, "free", false);
        boolean glow = Json.boolOr(obj, "glow", true);
        return new Tint(def, palette, free, glow);
    }

    /** True if a player may wear this cosmetic in {@code rgb} (0xRRGGBB). */
    public boolean allows(int rgb) {
        if (free || rgb == defaultColor) return true;
        for (int c : palette) {
            if (c == rgb) return true;
        }
        return false;
    }

    /** The colour actually shown for a requested one (null = not chosen): anything not allowed falls back. */
    public int effective(Integer requested) {
        return requested != null && allows(requested) ? requested : defaultColor;
    }

    private static int color(String text) throws ValidationException {
        if (!COLOR.matcher(text).matches()) throw new ValidationException("tint colours look like #RRGGBB");
        return Integer.parseInt(text.substring(1), 16);
    }

    /**
     * True if a texture pixel takes the tint: opaque enough and grey (red, green and blue almost equal).
     * Shared by the mod and the tools so both colour exactly the same pixels.
     */
    public static boolean tintable(int argb) {
        int a = argb >>> 24;
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        return a > 0 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 10;
    }

    /** Tints one pixel: grey pixels become {@code rgb} times their brightness; others are unchanged. */
    public static int apply(int argb, int rgb) {
        if (!tintable(argb)) return argb;
        int grey = ((argb >> 16 & 255) + (argb >> 8 & 255) + (argb & 255)) / 3;
        int r = (rgb >> 16 & 255) * grey / 255;
        int g = (rgb >> 8 & 255) * grey / 255;
        int b = (rgb & 255) * grey / 255;
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
