package com.heytozzz.htzcosmetics.mixin;

import com.heytozzz.htzcosmetics.client.PlayerUuidAccess;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(PlayerRenderState.class)
public class PlayerRenderStateMixin implements PlayerUuidAccess {
    @Unique
    private UUID htzcosmetics$uuid;

    @Override
    public UUID htzcosmetics$getUuid() {
        return htzcosmetics$uuid;
    }

    @Override
    public void htzcosmetics$setUuid(UUID uuid) {
        this.htzcosmetics$uuid = uuid;
    }
}
