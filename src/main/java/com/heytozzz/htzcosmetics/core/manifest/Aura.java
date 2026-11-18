package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;

import java.util.Map;

/**
 * Settings of an AURA cosmetic: a coloured effect around the whole body. Colours, cycle, pulse and bloom come from
 * the cosmetic's {@link Glow}.
 *
 * <pre>{@code
 * "aura": {
 *   "fill":    0.2,  // soft coloured light over the body, 0 = none
 *   "outline": 0.5   // thickness of the solid outline in model pixels, 0 = none
 * }
 * }</pre>
 */
public record Aura(double fill, double outline) {
    public static final double MAX_FILL = 0.6;
    public static final double MAX_OUTLINE = 1.5;

    public static Aura parse(Map<String, Object> obj) throws ValidationException {
        double fill = Json.numberOr(obj, "fill", 0, MAX_FILL, 0.2);
        double outline = Json.numberOr(obj, "outline", 0, MAX_OUTLINE, 0.5);
        return new Aura(fill, outline);
    }
}
