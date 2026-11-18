package com.heytozzz.htzcosmetics.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to Minecraft's own account service, used only to ask Mojang for the player certificate again
 * when Minecraft's usual certificate manager has none (see {@code OwnCosmetics#certificate}).
 */
@Mixin(Minecraft.class)
public interface UserApiAccessor {
    @Accessor("userApiService")
    UserApiService htzcosmetics$userApiService();
}
