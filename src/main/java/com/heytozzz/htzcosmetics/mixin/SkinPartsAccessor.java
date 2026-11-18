package com.heytozzz.htzcosmetics.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if >=1.21.9 {
/*import net.minecraft.world.entity.Avatar;
*///?} else {
import net.minecraft.world.entity.player.Player;
//?}

/** Read access to the synced skin-parts byte (where the change signal bit travels). */
//? if >=1.21.9 {
/*@Mixin(Avatar.class) // the player data moved to Avatar in 1.21.9
*///?} else {
@Mixin(Player.class)
//?}
public interface SkinPartsAccessor {
    @Accessor("DATA_PLAYER_MODE_CUSTOMISATION")
    static EntityDataAccessor<Byte> htzcosmetics$skinPartsData() {
        throw new AssertionError("mixin not applied");
    }
}
