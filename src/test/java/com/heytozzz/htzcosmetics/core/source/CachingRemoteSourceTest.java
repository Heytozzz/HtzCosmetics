package com.heytozzz.htzcosmetics.core.source;

import com.heytozzz.htzcosmetics.core.cache.AssetCache;
import com.heytozzz.htzcosmetics.core.cache.ManifestStore;
import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import com.heytozzz.htzcosmetics.core.net.TestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachingRemoteSourceTest {
    @TempDir
    Path dir;

    @Test
    void downloadsOnceThenUsesCacheAndFallsBackWhenOffline() throws Exception {
        byte[] manifest = "{\"m\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] sig = "c2ln".getBytes(StandardCharsets.US_ASCII);
        byte[] asset = "asset-bytes".getBytes(StandardCharsets.UTF_8);
        String sha = Hashes.sha256Hex(asset);
        AssetCache cache = new AssetCache(dir.resolve("cache"), 1 << 20);
        ManifestStore store = new ManifestStore(dir);

        try (TestServer server = new TestServer()) {
            server.routes.put("/manifest.json", TestServer.Reply.ok(manifest));
            server.routes.put("/manifest.json.sig", TestServer.Reply.ok(sig));
            server.routes.put("/a/" + sha, TestServer.Reply.ok(asset));
            CachingRemoteSource source = new CachingRemoteSource(server.client(), server.base(), cache, store);

            assertArrayEquals(manifest, source.manifest());
            assertArrayEquals(sig, source.signature());
            assertArrayEquals(asset, source.asset(sha, 100));
            assertArrayEquals(asset, source.asset(sha, 100));
            assertEquals(1, server.hits("/a/" + sha), "second read comes from the disk cache");
            source.onManifestAccepted(manifest, sig, 7);

            // A tampered cache file is detected and downloaded again.
            Files.writeString(dir.resolve("cache").resolve(sha), "evil");
            assertArrayEquals(asset, source.asset(sha, 100));
            assertEquals(2, server.hits("/a/" + sha));

            assertThrows(IOException.class, () -> source.asset("../../x", 100));
        }

        // Server gone: the stored (previously verified) manifest is offered again; assets come from cache.
        try (TestServer offline = new TestServer()) {
            CachingRemoteSource source = new CachingRemoteSource(offline.client(), offline.base(), cache, store);
            assertArrayEquals(manifest, source.manifest());
            assertArrayEquals(sig, source.signature());
            assertArrayEquals(asset, source.asset(sha, 100));
            assertEquals(7, source.lastAcceptedVersion());
        }
    }
}
