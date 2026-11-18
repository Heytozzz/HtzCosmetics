package com.heytozzz.htzcosmetics.client.source;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.crypto.Hashes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a built {@code site/} folder from disk (used for development and testing).
 * File names are never taken from data: assets are only looked up by a validated SHA-256 hex name.
 */
public final class LocalDirectorySource implements CosmeticSource {
    private final Path root;

    public LocalDirectorySource(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public byte[] manifest() throws IOException {
        return readCapped(root.resolve("manifest.json"), Limits.MAX_MANIFEST_BYTES);
    }

    @Override
    public byte[] signature() throws IOException {
        return readCapped(root.resolve("manifest.json.sig"), Limits.MAX_SIGNATURE_FILE_BYTES);
    }

    @Override
    public byte[] asset(String sha256, int maxSize) throws IOException {
        if (!Hashes.isSha256Hex(sha256)) throw new IOException("invalid asset name");
        return readCapped(root.resolve("a").resolve(sha256), maxSize);
    }

    @Override
    public String describe() {
        return "local folder " + root;
    }

    /** Reads at most {@code max} bytes; a longer file is an error (never loaded fully into memory). */
    static byte[] readCapped(Path file, int max) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] data = in.readNBytes(max + 1);
            if (data.length > max) throw new IOException(file.getFileName() + " is larger than allowed");
            return data;
        }
    }
}
