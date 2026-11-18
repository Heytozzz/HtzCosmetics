package com.heytozzz.htzcosmetics.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the synced skin-parts byte (where the change signal bit travels). */
@Mixin(Player.class)
public interface PlayerAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> htzcosmetics$skinPartsData() {
        throw new AssertionError("mixin not applied");
    }
}
