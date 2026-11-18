package com.heytozzz.htzcosmetics.mixin;

import com.heytozzz.htzcosmetics.client.PlayerUuidAccess;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
/*import net.minecraft.world.entity.Avatar;
*///?} else {
import net.minecraft.client.player.AbstractClientPlayer;
//?}

/** Copies the player's UUID into the render state (render states no longer keep a reference to the entity). */
@Mixin(PlayerRenderer.class)
public class RendererUuidMixin {
    //? if >=1.21.9 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL"))
    private void htzcosmetics$captureUuid(Avatar player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
        at = @At("TAIL"))
    private void htzcosmetics$captureUuid(AbstractClientPlayer player, PlayerRenderState state, float partialTick,
                                          CallbackInfo ci) {
    //?}
        ((PlayerUuidAccess) state).htzcosmetics$setUuid(player.getUUID());
    }
}
