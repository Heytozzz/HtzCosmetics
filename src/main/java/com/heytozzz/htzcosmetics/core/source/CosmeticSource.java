package com.heytozzz.htzcosmetics.core.source;

import java.io.IOException;

/**
 * Where the signed manifest and the assets come from (GitHub Pages with a disk cache, or a local folder in dev).
 * A source only returns raw bytes; everything it returns is untrusted and verified by the caller.
 */
public interface CosmeticSource {
    /** Fetches the manifest; {@link #signature()} then returns the signature fetched together with it. */
    byte[] manifest() throws IOException;

    byte[] signature() throws IOException;

    /**
     * @param sha256  asset hash from the verified manifest (64 lowercase hex chars)
     * @param maxSize the exact size promised by the manifest; implementations must not read more
     */
    byte[] asset(String sha256, int maxSize) throws IOException;

    /** Called after the manifest passed signature, expiry and rollback checks. */
    default void onManifestAccepted(byte[] manifest, byte[] signature, long version) throws IOException {
    }

    /** Highest manifest version this source has seen accepted before (anti-rollback), or 0. */
    default long lastAcceptedVersion() {
        return 0;
    }

    String describe();
}
