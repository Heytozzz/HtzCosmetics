package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A cosmetic ready to draw: its model baked into vanilla model parts and its texture uploaded to the GPU.
 *
 * @param bones      the baked part of each model bone (same order as the model), for animation
 * @param animations declarative clips; empty for static cosmetics
 */
public record BakedCosmetic(CosmeticDefinition definition, ModelPart root, ModelPart[] bones,
                            List<AnimationClip> animations, ResourceLocation texture, boolean translucent) {
}
