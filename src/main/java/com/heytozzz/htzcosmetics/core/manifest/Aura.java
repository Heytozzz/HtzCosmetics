package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;

/**
 * Settings of an AURA cosmetic: a coloured glow around the whole body, like the vanilla "glowing" effect but only
 * where the player is actually visible (never through walls). Colours, cycle, pulse and the wide bloom (strength
 * {@code bloom}, width {@code radius}) come from the cosmetic's {@link Glow}.
 *
 * <pre>{@code
 * "aura": {
 *   "outline": 1.0,          // width of the bright glowing edge in model pixels, 0 = none
 *   "outlineStrength": 0.9,  // its opacity, 0-1
 *   "fill": 0                // soft coloured light over the body itself, 0 = none
 * }
 * }</pre>
 */
public record Aura(double fill, double outline, double outlineStrength) {
    public static final double MAX_FILL = 0.6;
    public static final double MAX_OUTLINE = 3;
    public static final Aura DEFAULT = new Aura(0, 1, 0.9);

    public static Aura parse(java.util.Map<String, Object> obj) throws ValidationException {
        double fill = Json.numberOr(obj, "fill", 0, MAX_FILL, DEFAULT.fill());
        double outline = Json.numberOr(obj, "outline", 0, MAX_OUTLINE, DEFAULT.outline());
        double strength = Json.numberOr(obj, "outlineStrength", 0, 1, DEFAULT.outlineStrength());
        return new Aura(fill, outline, strength);
    }
}
