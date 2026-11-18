package com.heytozzz.htzcosmetics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent constants shared by every mod loader (Fabric now, NeoForge/Forge later).
 * Nothing in this class may import Fabric, NeoForge or Forge classes.
 */
public final class HtzCosmetics {
    public static final String MOD_ID = "htzcosmetics";

    /** Writes to the game console and to logs/latest.log, prefixed with our mod id. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Stonecutter replaces these literals with the real values at build time.
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.8";

    private HtzCosmetics() {
        // Utility class: no instances.
    }
}
