package com.heytozzz.htzcosmetics.core.cache;

import com.heytozzz.htzcosmetics.core.crypto.Hashes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Disk cache of downloaded assets. File names are ALWAYS the asset's SHA-256 (validated hex), never anything
 * from remote data, so no path traversal is possible. Contents are re-hashed on every read; a damaged or
 * tampered file is deleted and treated as missing. Least-recently-used files are removed above the size cap.
 */
public final class AssetCache {
    public static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;

    private final Path dir;
    private final long maxBytes;

    public AssetCache(Path dir, long maxBytes) {
        this.dir = dir.toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    /** @return the cached bytes, or null if absent, too large or corrupted */
    public byte[] get(String sha256, int maxSize) throws IOException {
        if (!Hashes.isSha256Hex(sha256)) return null;
        Path file = dir.resolve(sha256);
        if (!Files.isRegularFile(file)) return null;
        byte[] data;
        try (InputStream in = Files.newInputStream(file)) {
            data = in.readNBytes(maxSize + 1);
        }
        if (data.length > maxSize || !Hashes.matches(data, sha256)) {
            Files.deleteIfExists(file);
            return null;
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis())); // LRU "touch"
        return data;
    }

    /** Stores data only if it really has this hash. */
    public void put(String sha256, byte[] data) throws IOException {
        if (!Hashes.isSha256Hex(sha256) || !Hashes.matches(data, sha256)) return;
        Files.createDirectories(dir);
        Path tmp = dir.resolve(sha256 + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, dir.resolve(sha256), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        trim();
    }

    /** Deletes leftovers and the oldest entries until the cache is below 80% of its cap. */
    public void trim() throws IOException {
        if (!Files.isDirectory(dir)) return;
        List<Path> entries = new ArrayList<>();
        long total = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!Files.isRegularFile(p)) continue;
                if (!Hashes.isSha256Hex(name)) {
                    if (name.endsWith(".tmp")) Files.deleteIfExists(p);
                    continue; // never touch files we did not create
                }
                entries.add(p);
                total += Files.size(p);
            }
        }
        if (total <= maxBytes) return;
        entries.sort(Comparator.comparingLong(AssetCache::lastModified));
        long target = maxBytes * 8 / 10;
        for (Path p : entries) {
            if (total <= target) break;
            long size = Files.size(p);
            Files.deleteIfExists(p);
            total -= size;
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
