package com.heytozzz.htzcosmetics.core.cache;

import com.heytozzz.htzcosmetics.core.Limits;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the last manifest that passed verification (so cosmetics work offline and start instantly) and the
 * highest accepted manifest version (anti-rollback across restarts). Stored copies are re-verified on every use.
 */
public final class ManifestStore {
    private final Path dir;

    public record Stored(byte[] manifest, byte[] signature) {
    }

    public ManifestStore(Path dir) {
        this.dir = dir.toAbsolutePath().normalize();
    }

    /** @return the stored pair, or null if there is none */
    public Stored load() throws IOException {
        Path m = dir.resolve("manifest.json");
        Path s = dir.resolve("manifest.json.sig");
        if (!Files.isRegularFile(m) || !Files.isRegularFile(s)) return null;
        return new Stored(read(m, Limits.MAX_MANIFEST_BYTES), read(s, Limits.MAX_SIGNATURE_FILE_BYTES));
    }

    public void save(byte[] manifest, byte[] signature, long version) throws IOException {
        Files.createDirectories(dir);
        write("manifest.json", manifest);
        write("manifest.json.sig", signature);
        if (version > lastAcceptedVersion()) {
            write("version.txt", Long.toString(version).getBytes(StandardCharsets.US_ASCII));
        }
    }

    /** Highest manifest version accepted on this computer, or 0. */
    public long lastAcceptedVersion() {
        try {
            Path f = dir.resolve("version.txt");
            if (!Files.isRegularFile(f)) return 0;
            String text = new String(read(f, 32), StandardCharsets.US_ASCII).trim();
            if (!text.matches("[0-9]{1,18}")) return 0;
            return Long.parseLong(text);
        } catch (IOException e) {
            return 0;
        }
    }

    private void write(String name, byte[] data) throws IOException {
        Path tmp = dir.resolve(name + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static byte[] read(Path file, int max) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] data = in.readNBytes(max + 1);
            if (data.length > max) throw new IOException(file.getFileName() + " too large");
            return data;
        }
    }
}
