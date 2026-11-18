package com.heytozzz.htzcosmetics.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** Read-only access to a model part's children and boxes (used to follow parts moved by animation mods). */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> htzcosmetics$children();

    @Accessor("cubes")
    List<ModelPart.Cube> htzcosmetics$cubes();
}
