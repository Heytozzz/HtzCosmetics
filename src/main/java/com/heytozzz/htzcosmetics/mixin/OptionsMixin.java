package com.heytozzz.htzcosmetics.mixin;

import com.heytozzz.htzcosmetics.client.ChangeSignal;
import com.heytozzz.htzcosmetics.core.signal.ChangeSignalPolicy;
import net.minecraft.client.Options;
import net.minecraft.server.level.ClientInformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the "cosmetics changed" signal bit to the skin-parts byte the client sends to the server.
 * Vanilla only uses the low 7 bits, so this has no visible effect for anyone.
 */
@Mixin(Options.class)
public class OptionsMixin {
    @Inject(method = "buildPlayerInformation", at = @At("RETURN"), cancellable = true)
    private void htzcosmetics$addSignalBit(CallbackInfoReturnable<ClientInformation> cir) {
        if (!ChangeSignal.bit()) return;
        ClientInformation i = cir.getReturnValue();
        cir.setReturnValue(new ClientInformation(i.language(), i.viewDistance(), i.chatVisibility(), i.chatColors(),
            i.modelCustomisation() | ChangeSignalPolicy.SIGNAL_BIT, i.mainHand(), i.textFilteringEnabled(),
            i.allowsListing(), i.particleStatus()));
    }
}
