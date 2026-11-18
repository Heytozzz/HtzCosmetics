package com.heytozzz.htzcosmetics.core.model;

import java.util.List;

/**
 * A group of cubes that rotates around {@code pivot}. Bones form a tree through {@code parentIndex}.
 *
 * @param parentIndex index of the parent bone in {@link CosmeticModel#bones()}, or -1 for a root bone
 *                    (root bones are attached to the cosmetic's anchor body part)
 * @param rotation    degrees around X, Y, Z
 */
public record Bone(String name, int parentIndex, double[] pivot, double[] rotation, List<Cube> cubes) {
}
