package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.cache.AssetCache;
import com.heytozzz.htzcosmetics.core.cache.ManifestStore;
import com.heytozzz.htzcosmetics.core.net.BackendClient;
import com.heytozzz.htzcosmetics.core.net.Endpoints;
import com.heytozzz.htzcosmetics.core.net.SecureHttp;
import com.heytozzz.htzcosmetics.core.source.CachingRemoteSource;
import com.heytozzz.htzcosmetics.core.source.CosmeticSource;
import com.heytozzz.htzcosmetics.core.source.LocalDirectorySource;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Loader-independent startup logic shared by every loader entrypoint. */
public final class ClientBootstrap {
    /** JVM property pointing to a local, built {@code site/} folder (set by the dev run config only). */
    public static final String DEV_SITE_PROPERTY = "htzcosmetics.devSite";

    private static SecureHttp http;
    private static CosmeticSource source;
    private static BackendClient backend;

    private ClientBootstrap() {
    }

    /** Called once when the client has started. */
    public static synchronized void init() {
        if (source != null) return;
        http = SecureHttp.production("HtzCosmetics/" + HtzCosmetics.VERSION + " (Minecraft " + HtzCosmetics.MINECRAFT + ")");
        backend = new BackendClient(http, Endpoints.BACKEND);

        String devSite = System.getProperty(DEV_SITE_PROPERTY);
        if (devSite != null && !devSite.isBlank() && Files.isDirectory(Path.of(devSite))) {
            // Still fully verified: signature, hashes and all limits apply to local files too.
            source = new LocalDirectorySource(Path.of(devSite));
        } else {
            Path base = Minecraft.getInstance().gameDirectory.toPath().resolve(HtzCosmetics.MOD_ID);
            source = new CachingRemoteSource(http, Endpoints.SITE,
                new AssetCache(base.resolve("cache"), AssetCache.DEFAULT_MAX_BYTES), new ManifestStore(base));
        }
        HtzCosmetics.LOGGER.info("Cosmetics source: {}", source.describe());
        loadCosmetics(true);
    }

    /** Background (re)load; never blocks the caller. */
    public static void loadCosmetics(boolean force) {
        if (source != null) CosmeticLibrary.get().reload(source, force);
    }

    public static BackendClient backend() {
        return backend;
    }
}
