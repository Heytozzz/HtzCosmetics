package com.heytozzz.htzcosmetics.core.model;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;

import java.util.List;

/**
 * A group of cubes that rotates around {@code pivot}. Bones form a tree through {@code parentIndex}.
 *
 * @param parentIndex index of the parent bone in {@link CosmeticModel#bones()}, or -1 for a root bone
 *                    (root bones are attached to a body part)
 * @param rotation    degrees around X, Y, Z
 * @param anchor      body part this ROOT bone follows, or null for the cosmetic's own anchor (always null for
 *                    child bones, which follow their parent). Lets clothing put sleeves on the arms, and so on.
 */
public record Bone(String name, int parentIndex, double[] pivot, double[] rotation, List<Cube> cubes, Anchor anchor) {
}
