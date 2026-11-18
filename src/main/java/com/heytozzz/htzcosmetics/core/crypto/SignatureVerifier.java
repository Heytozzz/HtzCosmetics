package com.heytozzz.htzcosmetics.core.crypto;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Verifies Ed25519 signatures with the public keys embedded in the mod (built into Java 15+, no libraries).
 * Several keys are allowed so a backup key can take over if the primary one is ever compromised.
 */
public final class SignatureVerifier {
    /** Standard X.509 header that wraps a raw 32-byte Ed25519 public key. */
    private static final byte[] X509_ED25519_PREFIX = Hashes.fromHex("302a300506032b6570032100");
    private static final int SIGNATURE_LENGTH = 64;

    private final List<PublicKey> keys;

    private SignatureVerifier(List<PublicKey> keys) {
        this.keys = List.copyOf(keys);
    }

    /** @param base64Keys raw 32-byte Ed25519 public keys, Base64-encoded */
    public static SignatureVerifier fromBase64Keys(String... base64Keys) {
        List<PublicKey> keys = new ArrayList<>();
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            for (String b64 : base64Keys) {
                byte[] raw = Base64.getDecoder().decode(b64.trim());
                if (raw.length != 32) throw new IllegalArgumentException("Ed25519 public key must be 32 bytes");
                byte[] encoded = new byte[X509_ED25519_PREFIX.length + raw.length];
                System.arraycopy(X509_ED25519_PREFIX, 0, encoded, 0, X509_ED25519_PREFIX.length);
                System.arraycopy(raw, 0, encoded, X509_ED25519_PREFIX.length, raw.length);
                keys.add(factory.generatePublic(new X509EncodedKeySpec(encoded)));
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid embedded public key", e);
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("at least one key required");
        return new SignatureVerifier(keys);
    }

    /** True if any trusted key produced {@code signature} over exactly these {@code data} bytes. */
    public boolean verify(byte[] data, byte[] signature) {
        if (data == null || signature == null || signature.length != SIGNATURE_LENGTH) return false;
        for (PublicKey key : keys) {
            try {
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(data);
                if (verifier.verify(signature)) return true;
            } catch (GeneralSecurityException | RuntimeException e) {
                // Malformed signature for this key: try the next one.
            }
        }
        return false;
    }

    /**
     * Decodes a detached signature file: one line of Base64 text (whitespace around it is ignored).
     */
    public static byte[] decodeSignatureFile(byte[] fileBytes) throws ValidationException {
        if (fileBytes == null || fileBytes.length == 0) throw new ValidationException("signature file is empty");
        if (fileBytes.length > Limits.MAX_SIGNATURE_FILE_BYTES) throw new ValidationException("signature file too large");
        for (byte b : fileBytes) {
            if (b < 0) throw new ValidationException("signature file is not ASCII");
        }
        try {
            byte[] sig = Base64.getDecoder().decode(new String(fileBytes, StandardCharsets.US_ASCII).trim());
            if (sig.length != SIGNATURE_LENGTH) throw new ValidationException("signature must be 64 bytes");
            return sig;
        } catch (IllegalArgumentException e) {
            throw new ValidationException("signature is not valid Base64");
        }
    }
}
