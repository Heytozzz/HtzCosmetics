package com.heytozzz.htzcosmetics.core.cache;

import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheTest {
    @TempDir
    Path dir;

    private static byte[] bytes(int n, int seed) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (i * 31 + seed);
        return b;
    }

    @Test
    void storesOnlyByHashAndDetectsTampering() throws Exception {
        AssetCache cache = new AssetCache(dir, 1 << 20);
        byte[] data = bytes(100, 1);
        String sha = Hashes.sha256Hex(data);
        cache.put(sha, data);
        assertArrayEquals(data, cache.get(sha, 1000));

        cache.put(Hashes.sha256Hex(bytes(5, 9)), data); // wrong hash: ignored
        assertEquals(1, Files.list(dir).count());

        assertNull(cache.get("../../secret", 1000));
        assertNull(cache.get(sha.toUpperCase(), 1000));

        Files.write(dir.resolve(sha), "tampered".getBytes(StandardCharsets.UTF_8));
        assertNull(cache.get(sha, 1000));
        assertFalse(Files.exists(dir.resolve(sha)), "a corrupted entry is deleted");
    }

    @Test
    void evictsLeastRecentlyUsedAndNeverTouchesForeignFiles() throws Exception {
        AssetCache cache = new AssetCache(dir, 1000);
        Files.createDirectories(dir);
        Path foreign = dir.resolve("notes.txt");
        Files.writeString(foreign, "x".repeat(5000));
        String[] shas = new String[4];
        for (int i = 0; i < 4; i++) {
            byte[] d = bytes(300, i);
            shas[i] = Hashes.sha256Hex(d);
            cache.put(shas[i], d);
            Files.setLastModifiedTime(dir.resolve(shas[i]), FileTime.fromMillis(1_000_000L * (i + 1)));
        }
        cache.trim();
        assertTrue(Files.exists(foreign));
        assertFalse(Files.exists(dir.resolve(shas[0])), "oldest removed");
        assertTrue(Files.exists(dir.resolve(shas[3])), "newest kept");
    }

    @Test
    void manifestStoreKeepsHighestVersion() throws Exception {
        ManifestStore store = new ManifestStore(dir);
        assertEquals(0, store.lastAcceptedVersion());
        assertNull(store.load());
        store.save("{}".getBytes(), "sig".getBytes(), 50);
        store.save("{}".getBytes(), "sig".getBytes(), 40);
        assertEquals(50, store.lastAcceptedVersion());
        assertArrayEquals("sig".getBytes(), store.load().signature());
        Files.writeString(dir.resolve("version.txt"), "-5; DROP");
        assertEquals(0, store.lastAcceptedVersion());
    }
}
