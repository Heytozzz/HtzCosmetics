package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A cosmetic ready to draw: its model baked into vanilla model parts and its texture uploaded to the GPU.
 * A model-less aura has no {@code main} tree and no texture.
 *
 * @param main       the model, split by the body part each piece follows; null for a model-less aura
 * @param bloom      growing copies of the model for the glow's bloom (empty without bloom)
 * @param animations declarative clips; empty for static cosmetics
 * @param center     model bounding-box center in model pixels (for thumbnails)
 * @param size       largest bounding-box side in model pixels (for thumbnails)
 * @param aura       boxes drawn around the body for AURA cosmetics, else null
 */
public record BakedCosmetic(CosmeticDefinition definition, CosmeticBaker.Tree main, List<CosmeticBaker.Tree> bloom,
                            List<AnimationClip> animations, ResourceLocation texture, boolean translucent,
                            float[] center, float size, AuraModel.Geometry aura) {

    private static final ModelPart[] NO_BONES = new ModelPart[0];

    /** Each bone's part, for animation (empty for a model-less aura). */
    public ModelPart[] bones() {
        return main != null ? main.bones() : NO_BONES;
    }
}
