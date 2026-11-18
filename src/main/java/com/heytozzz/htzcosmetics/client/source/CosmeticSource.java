package com.heytozzz.htzcosmetics.client.source;

import java.io.IOException;

/**
 * Where the signed manifest and the assets come from (a local folder now, HTTPS later).
 * A source only returns raw bytes; everything it returns is untrusted and verified by the caller.
 */
public interface CosmeticSource {
    byte[] manifest() throws IOException;

    byte[] signature() throws IOException;

    /**
     * @param sha256  asset hash from the verified manifest (64 lowercase hex chars)
     * @param maxSize the exact size promised by the manifest; implementations must not read more
     */
    byte[] asset(String sha256, int maxSize) throws IOException;

    String describe();
}
