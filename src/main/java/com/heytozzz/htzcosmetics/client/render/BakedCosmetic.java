package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/** A cosmetic ready to draw: its model baked into vanilla model parts and its texture uploaded to the GPU. */
public record BakedCosmetic(CosmeticDefinition definition, ModelPart root, ResourceLocation texture, boolean translucent) {
}
