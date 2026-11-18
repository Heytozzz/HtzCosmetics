package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.crypto.SignatureVerifier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestParserTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final long NOW = 1_800_000_000L;

    static String cosmetic(String id, String category, String anchor, String rarity, int minFormat, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":{\"en_us\":\"Top hat\",\"es_es\":\"Chistera\"},"
            + "\"category\":\"" + category + "\",\"rarity\":\"" + rarity + "\",\"anchor\":\"" + anchor + "\","
            + "\"minFormat\":" + minFormat + ","
            + "\"model\":{\"sha256\":\"" + HASH_A + "\",\"size\":100},"
            + "\"texture\":{\"sha256\":\"" + HASH_B + "\",\"size\":200}" + extra + "}";
    }

    static String validCosmetic(String id) {
        return cosmetic(id, "HEAD", "HEAD", "RARE", 1, "");
    }

    static byte[] manifest(int format, long version, long expiresAt, String... cosmetics) {
        return ("{\"format\":" + format + ",\"manifestVersion\":" + version + ",\"expiresAt\":" + expiresAt
            + ",\"cosmetics\":[" + String.join(",", cosmetics) + "]}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesValidManifest() throws Exception {
        Manifest m = ManifestParser.parseUnverified(manifest(1, 5, NOW + 1000, validCosmetic("top_hat"),
            cosmetic("wings", "BACK", "BODY", "LEGENDARY", 1, ""),
            cosmetic("parrot", "SHOULDER", "LEFT_ARM", "COMMON", 1, ""),
            cosmetic("belt", "WAIST", "BODY", "UNCOMMON", 1, ",\"futureField\":{\"x\":1}")));
        assertEquals(4, m.cosmetics().size());
        assertEquals(0, m.skippedEntries());
        CosmeticDefinition hat = m.byId().get("top_hat");
        assertEquals("Chistera", hat.displayName("es_es"));
        assertEquals("Top hat", hat.displayName("es_ar")); // falls back to English
        assertEquals(Rarity.RARE, hat.rarity());
    }

    @Test
    void skipsBadEntriesButKeepsGoodOnes() throws Exception {
        Manifest m = ManifestParser.parseUnverified(manifest(1, 1, NOW + 1000,
            validCosmetic("ok"),
            validCosmetic("../../evil"),                                 // path traversal id
            validCosmetic("UPPER"),                                      // id charset
            validCosmetic("a".repeat(33)),                               // id too long
            validCosmetic("ok"),                                         // duplicate id
            cosmetic("future", "HEAD", "HEAD", "RARE", 2, ""),          // needs newer mod
            cosmetic("pet", "PET", "BODY", "RARE", 1, ""),               // unknown category
            cosmetic("aura", "HEAD", "HEAD", "MYTHIC", 1, ""),           // unknown rarity
            cosmetic("wrong", "HEAD", "LEFT_LEG", "RARE", 1, ""),        // anchor not allowed for category
            validCosmetic("bad_hash").replace(HASH_A, "../" + "a".repeat(61)),
            validCosmetic("huge").replace("\"size\":200", "\"size\":999999999"),
            validCosmetic("no_en").replace("\"en_us\"", "\"de_de\""),
            validCosmetic("evil_name").replace("Top hat", "\\u00a7kObfuscated"),
            validCosmetic("bidi_name").replace("Top hat", "\\u202eevil"),
            "\"not an object\""));
        assertEquals(1, m.cosmetics().size());
        assertEquals("ok", m.cosmetics().get(0).id());
        assertEquals(14, m.skippedEntries());
    }

    @Test
    void rejectsUnsupportedManifestFormat() {
        assertThrows(ValidationException.class,
            () -> ManifestParser.parseUnverified(manifest(2, 1, NOW + 1000, validCosmetic("a"))));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ValidationException.class,
            () -> ManifestParser.parseUnverified("[]".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ValidationException.class,
            () -> ManifestParser.parseUnverified("{\"format\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    // --- Signature + freshness + rollback ---

    private static KeyPair newKey() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static SignatureVerifier verifierFor(KeyPair key) {
        byte[] x509 = key.getPublic().getEncoded();
        return SignatureVerifier.fromBase64Keys(
            Base64.getEncoder().encodeToString(Arrays.copyOfRange(x509, x509.length - 32, x509.length)));
    }

    private static byte[] sigFile(KeyPair key, byte[] data) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key.getPrivate());
        s.update(data);
        return Base64.getEncoder().encode(s.sign());
    }

    @Test
    void verifyAndParseHappyPath() throws Exception {
        KeyPair key = newKey();
        byte[] data = manifest(1, 10, NOW + 1000, validCosmetic("a"));
        Manifest m = ManifestParser.verifyAndParse(data, sigFile(key, data), verifierFor(key), NOW, 9);
        assertEquals(10, m.manifestVersion());
        assertTrue(m.byId().containsKey("a"));
    }

    @Test
    void verifyAndParseRejectsAttacks() throws Exception {
        KeyPair key = newKey();
        KeyPair attacker = newKey();
        SignatureVerifier verifier = verifierFor(key);
        byte[] data = manifest(1, 10, NOW + 1000, validCosmetic("a"));
        byte[] sig = sigFile(key, data);

        // Modified after signing (e.g. an extra cosmetic injected).
        byte[] tampered = manifest(1, 10, NOW + 1000, validCosmetic("a"), validCosmetic("b"));
        assertThrows(ValidationException.class, () -> ManifestParser.verifyAndParse(tampered, sig, verifier, NOW, 0));
        // Signed by someone else.
        assertThrows(ValidationException.class,
            () -> ManifestParser.verifyAndParse(data, sigFile(attacker, data), verifier, NOW, 0));
        // Expired.
        assertThrows(ValidationException.class, () -> ManifestParser.verifyAndParse(data, sig, verifier, NOW + 5000, 0));
        // Rollback to an older, still validly signed manifest.
        assertThrows(ValidationException.class, () -> ManifestParser.verifyAndParse(data, sig, verifier, NOW, 11));
        // Missing signature.
        assertThrows(ValidationException.class, () -> ManifestParser.verifyAndParse(data, new byte[0], verifier, NOW, 0));
    }
}
