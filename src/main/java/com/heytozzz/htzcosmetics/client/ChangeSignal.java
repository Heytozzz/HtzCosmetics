package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.core.signal.ChangeSignalPolicy;
import net.minecraft.client.Minecraft;

/**
 * Sender side of the "my cosmetics changed" signal (see {@link ChangeSignalPolicy}).
 * Flipping the bit just re-sends the normal client options packet; the server relays it to nearby players.
 */
public final class ChangeSignal {
    private static final ChangeSignalPolicy.Sender SENDER = new ChangeSignalPolicy.Sender();
    private static volatile boolean bit;

    private ChangeSignal() {
    }

    /** Read by the options mixin when the client options packet is built. */
    public static boolean bit() {
        return bit;
    }

    /** Client thread: announce a change now, or as soon as the cooldown allows. */
    public static void send() {
        if (!ClientState.config().sendChangeSignal) return;
        if (SENDER.request(System.currentTimeMillis())) flip();
    }

    /** Client thread, every tick. */
    public static void tick() {
        if (SENDER.poll(System.currentTimeMillis())) flip();
    }

    private static void flip() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return; // not on a server: nobody to tell
        bit = !bit;
        mc.options.broadcastOptions();
    }
}
