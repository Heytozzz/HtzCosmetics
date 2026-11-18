package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Optional glow effect of a cosmetic: plain numbers and colours, nothing executable.
 *
 * <pre>{@code
 * "glow": {
 *   "colors":   ["#FFD24A", "#FF6AD5"],  // 1-8 colours; several colours blend into each other in a loop
 *   "cycle":    4,      // seconds for a full colour loop (only with 2+ colours)
 *   "emissive": true,   // draw the cosmetic at full brightness (also in the dark); shader packs add their own bloom
 *   "bloom":    0.6,    // strength of the coloured halo around it, 0 = none
 *   "radius":   1.0,    // size of the halo in model pixels
 *   "pulse":    0       // seconds per breathing pulse, 0 = steady
 * }
 * }</pre>
 *
 * @param colors 0xRRGGBB values
 */
public record Glow(int[] colors, double cycle, boolean emissive, double bloom, double radius, double pulse) {
    public static final int MAX_COLORS = 8;
    public static final double MIN_SECONDS = 0.25;
    public static final double MAX_SECONDS = 60;
    public static final double MAX_RADIUS = 3;
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

    /** Parses and validates the "glow" object; any problem rejects the cosmetic. */
    public static Glow parse(Map<String, Object> obj) throws ValidationException {
        List<Object> list = Json.array(obj, "colors", MAX_COLORS);
        if (list.isEmpty()) throw new ValidationException("glow needs at least one colour");
        int[] colors = new int[list.size()];
        for (int i = 0; i < colors.length; i++) {
            String text = Json.asString(list.get(i), "colour", 7);
            if (!COLOR.matcher(text).matches()) throw new ValidationException("glow colours look like #RRGGBB");
            colors[i] = Integer.parseInt(text.substring(1), 16);
        }
        double cycle = Json.numberOr(obj, "cycle", MIN_SECONDS, MAX_SECONDS, 4);
        boolean emissive = Json.boolOr(obj, "emissive", true);
        double bloom = Json.numberOr(obj, "bloom", 0, 1, 0.6);
        double radius = Json.numberOr(obj, "radius", 0.1, MAX_RADIUS, 1);
        double pulse = Json.numberOr(obj, "pulse", 0, MAX_SECONDS, 0);
        if (pulse != 0 && pulse < MIN_SECONDS) throw new ValidationException("glow pulse must be 0 or at least 0.25 s");
        return new Glow(colors, cycle, emissive, bloom, radius, pulse);
    }

    /** Colour (0xRRGGBB) at {@code seconds}, blending through the colour list in a loop. */
    public int colorAt(double seconds) {
        if (colors.length == 1) return colors[0];
        double pos = (seconds / cycle % 1.0 + 1.0) % 1.0 * colors.length;
        int i = (int) pos;
        double f = pos - i;
        int a = colors[i % colors.length];
        int b = colors[(i + 1) % colors.length];
        return mix(a >> 16 & 255, b >> 16 & 255, f) << 16 | mix(a >> 8 & 255, b >> 8 & 255, f) << 8 | mix(a & 255, b & 255, f);
    }

    /** Brightness factor 0.55..1 for the breathing pulse (1 when steady). */
    public double pulseAt(double seconds) {
        if (pulse == 0) return 1;
        return 0.775 + 0.225 * Math.cos(seconds / pulse * 2 * Math.PI);
    }

    private static int mix(int a, int b, double f) {
        return (int) Math.round(a + (b - a) * f);
    }
}
