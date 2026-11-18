package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.client.source.LocalDirectorySource;

import java.nio.file.Files;
import java.nio.file.Path;

/** Loader-independent startup logic shared by every loader entrypoint. */
public final class ClientBootstrap {
    /** JVM property pointing to a local, built {@code site/} folder (set automatically by the dev run configs). */
    public static final String DEV_SITE_PROPERTY = "htzcosmetics.devSite";

    private ClientBootstrap() {
    }

    public static void loadCosmetics() {
        String devSite = System.getProperty(DEV_SITE_PROPERTY);
        if (devSite != null && !devSite.isBlank() && Files.isDirectory(Path.of(devSite))) {
            // Still fully verified: signature, hashes and all limits apply to local files too.
            CosmeticLibrary.get().reload(new LocalDirectorySource(Path.of(devSite)));
        } else {
            HtzCosmetics.LOGGER.info("No cosmetic source configured yet (online loading arrives in a later version)");
        }
    }
}
