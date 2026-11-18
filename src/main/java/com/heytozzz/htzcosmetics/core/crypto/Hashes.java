package com.heytozzz.htzcosmetics.core.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers. Hashes are always 64 lowercase hex characters. */
public final class Hashes {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hashes() {
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to support SHA-256.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return toHex(sha256(data));
    }

    /** True only for exactly 64 lowercase hex chars - safe to use as a file name. */
    public static boolean isSha256Hex(String s) {
        if (s == null || s.length() != 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    /** Checks that {@code data} has exactly the expected SHA-256. */
    public static boolean matches(byte[] data, String expectedHex) {
        if (data == null || !isSha256Hex(expectedHex)) return false;
        return MessageDigest.isEqual(sha256(data), fromHex(expectedHex));
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        if (hex.length() % 2 != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("invalid hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
