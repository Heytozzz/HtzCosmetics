package com.heytozzz.htzcosmetics.core.crypto;

import com.heytozzz.htzcosmetics.core.ValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureVerifierTest {
    /** Test helper: a fresh key pair plus its raw public key in Base64 (the format the mod embeds). */
    static final class TestKey {
        final KeyPair pair;
        final String publicB64;

        TestKey() throws Exception {
            pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] x509 = pair.getPublic().getEncoded();
            publicB64 = Base64.getEncoder().encodeToString(Arrays.copyOfRange(x509, x509.length - 32, x509.length));
        }

        byte[] sign(byte[] data) throws Exception {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(pair.getPrivate());
            s.update(data);
            return s.sign();
        }

        byte[] signatureFile(byte[] data) throws Exception {
            return (Base64.getEncoder().encodeToString(sign(data)) + "\n").getBytes(StandardCharsets.US_ASCII);
        }
    }

    private static final byte[] DATA = "{\"format\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsValidSignature() throws Exception {
        TestKey key = new TestKey();
        SignatureVerifier verifier = SignatureVerifier.fromBase64Keys(key.publicB64);
        assertTrue(verifier.verify(DATA, key.sign(DATA)));
    }

    @Test
    void rejectsTamperedData() throws Exception {
        TestKey key = new TestKey();
        byte[] sig = key.sign(DATA);
        byte[] tampered = DATA.clone();
        tampered[3] ^= 1;
        assertFalse(SignatureVerifier.fromBase64Keys(key.publicB64).verify(tampered, sig));
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        TestKey key = new TestKey();
        byte[] sig = key.sign(DATA);
        sig[10] ^= 1;
        assertFalse(SignatureVerifier.fromBase64Keys(key.publicB64).verify(DATA, sig));
    }

    @Test
    void rejectsSignatureFromUntrustedKey() throws Exception {
        TestKey trusted = new TestKey();
        TestKey attacker = new TestKey();
        assertFalse(SignatureVerifier.fromBase64Keys(trusted.publicB64).verify(DATA, attacker.sign(DATA)));
    }

    @Test
    void backupKeyAlsoWorks() throws Exception {
        TestKey primary = new TestKey();
        TestKey backup = new TestKey();
        SignatureVerifier verifier = SignatureVerifier.fromBase64Keys(primary.publicB64, backup.publicB64);
        assertTrue(verifier.verify(DATA, backup.sign(DATA)));
    }

    @Test
    void rejectsWrongLengthAndNull() throws Exception {
        TestKey key = new TestKey();
        SignatureVerifier verifier = SignatureVerifier.fromBase64Keys(key.publicB64);
        assertFalse(verifier.verify(DATA, new byte[63]));
        assertFalse(verifier.verify(DATA, new byte[64]));
        assertFalse(verifier.verify(DATA, null));
        assertFalse(verifier.verify(null, key.sign(DATA)));
    }

    @Test
    void signatureFileDecoding() throws Exception {
        TestKey key = new TestKey();
        assertEquals(64, SignatureVerifier.decodeSignatureFile(key.signatureFile(DATA)).length);
        assertThrows(ValidationException.class, () -> SignatureVerifier.decodeSignatureFile(new byte[0]));
        assertThrows(ValidationException.class, () -> SignatureVerifier.decodeSignatureFile("not base64 !!".getBytes()));
        assertThrows(ValidationException.class, () -> SignatureVerifier.decodeSignatureFile(new byte[1000]));
        assertThrows(ValidationException.class, () -> SignatureVerifier.decodeSignatureFile("QUJD".getBytes()));
    }

    @Test
    void hashHelpers() {
        String hex = Hashes.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex);
        assertTrue(Hashes.matches("abc".getBytes(StandardCharsets.UTF_8), hex));
        assertFalse(Hashes.matches("abd".getBytes(StandardCharsets.UTF_8), hex));
        assertFalse(Hashes.isSha256Hex("../../../../etc/passwd"));
        assertFalse(Hashes.isSha256Hex(hex.toUpperCase()));
        assertFalse(Hashes.isSha256Hex(hex + "0"));
    }
}
