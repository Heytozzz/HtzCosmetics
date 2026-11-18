package com.heytozzz.htzcosmetics.core.model;

import java.util.List;

/**
 * A validated cosmetic model. Bones are ordered so that every parent comes before its children.
 */
public record CosmeticModel(int format, int textureWidth, int textureHeight, List<Bone> bones, int cubeCount) {
}
