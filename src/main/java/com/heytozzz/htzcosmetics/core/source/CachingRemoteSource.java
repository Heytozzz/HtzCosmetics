package com.heytozzz.htzcosmetics.core.source;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.cache.AssetCache;
import com.heytozzz.htzcosmetics.core.cache.ManifestStore;
import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import com.heytozzz.htzcosmetics.core.net.SecureHttp;

import java.io.IOException;
import java.net.URI;

/**
 * Production source: signed manifest and assets from GitHub Pages, with a disk cache.
 * If the network is down, the last verified manifest is used (it is verified again, including expiry).
 * Assets are cached by hash, so each one is downloaded once.
 */
public final class CachingRemoteSource implements CosmeticSource {
    private final SecureHttp http;
    private final URI site;
    private final AssetCache cache;
    private final ManifestStore store;
    private byte[] signature;

    public CachingRemoteSource(SecureHttp http, URI site, AssetCache cache, ManifestStore store) {
        this.http = http;
        this.site = site;
        this.cache = cache;
        this.store = store;
    }

    @Override
    public byte[] manifest() throws IOException {
        try {
            byte[] manifest = http.get(site.resolve("manifest.json"), Limits.MAX_MANIFEST_BYTES);
            signature = http.get(site.resolve("manifest.json.sig"), Limits.MAX_SIGNATURE_FILE_BYTES);
            return manifest;
        } catch (IOException networkError) {
            ManifestStore.Stored stored = store.load();
            if (stored == null) throw networkError;
            signature = stored.signature();
            return stored.manifest();
        }
    }

    @Override
    public byte[] signature() throws IOException {
        if (signature == null) throw new IOException("manifest() must be called first");
        return signature;
    }

    @Override
    public byte[] asset(String sha256, int maxSize) throws IOException {
        if (!Hashes.isSha256Hex(sha256)) throw new IOException("invalid asset name");
        byte[] cached = cache.get(sha256, maxSize);
        if (cached != null) return cached;
        byte[] data = http.get(site.resolve("a/" + sha256), maxSize);
        cache.put(sha256, data); // ignored unless the hash matches
        return data;
    }

    @Override
    public void onManifestAccepted(byte[] manifest, byte[] signature, long version) throws IOException {
        store.save(manifest, signature, version);
    }

    @Override
    public long lastAcceptedVersion() {
        return store.lastAcceptedVersion();
    }

    @Override
    public String describe() {
        return site.getHost();
    }
}
