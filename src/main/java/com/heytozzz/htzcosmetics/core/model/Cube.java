package com.heytozzz.htzcosmetics.core.model;

/**
 * A textured box using Minecraft "box UV" layout (same as vanilla entity models).
 * Coordinates are in model pixels, relative to the owning bone, Y pointing down (Minecraft convention).
 *
 * @param origin  minimum corner (x, y, z)
 * @param size    width, height, depth (0 allowed for flat planes)
 * @param u       texture X of the box UV layout
 * @param v       texture Y of the box UV layout
 * @param inflate grows the box in every direction without changing UVs
 * @param mirror  mirrors the texture horizontally
 */
public record Cube(double[] origin, double[] size, int u, int v, double inflate, boolean mirror) {
}
