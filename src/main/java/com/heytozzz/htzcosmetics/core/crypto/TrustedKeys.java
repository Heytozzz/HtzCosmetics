package com.heytozzz.htzcosmetics.core.crypto;

import java.util.List;

/**
 * Public keys the mod trusts to sign the cosmetics manifest (raw 32-byte Ed25519 keys, Base64).
 *
 * <p>The list between the BEGIN/END markers is written by the offline key tool
 * ({@code gradlew htzKeygen}). The first key is the primary signing key, the second is an offline backup
 * that can take over if the primary is lost or leaked ({@code gradlew htzKeygen --args="--rotate"}).
 * Public keys are not secret; the matching private keys never leave the owner's computer.
 */
public final class TrustedKeys {
    // BEGIN TRUSTED KEYS (managed by the htzKeygen tool, do not edit by hand)
    private static final String[] KEYS = {
        "iaRK8nm+3QTZi5QwoynZE9MqrZSSozYvDwlIjanszSE=", // primary
        "FwQP5T6CVls4DudiqH5q7GuEW+75am7MFTY5YxD3Je4=", // backup
    };
    // END TRUSTED KEYS

    private TrustedKeys() {
    }

    public static List<String> base64Keys() {
        return List.of(KEYS);
    }

    /** False until the owner has generated keys; with no keys every manifest is rejected. */
    public static boolean isConfigured() {
        return KEYS.length > 0;
    }

    /** @throws IllegalStateException if no keys are configured */
    public static SignatureVerifier verifier() {
        if (!isConfigured()) throw new IllegalStateException("No trusted manifest keys configured");
        return SignatureVerifier.fromBase64Keys(KEYS);
    }
}
