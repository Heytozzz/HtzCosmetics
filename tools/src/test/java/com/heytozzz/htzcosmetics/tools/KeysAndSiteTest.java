package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import com.heytozzz.htzcosmetics.core.manifest.Manifest;
import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeysAndSiteTest {
    private static final long NOW = 1_800_000_000L;
    private static final String TRUSTED_TEMPLATE = """
        public final class TrustedKeys {
            // BEGIN TRUSTED KEYS (managed by the htzKeygen tool, do not edit by hand)
            private static final String[] KEYS = {
            };
            // END TRUSTED KEYS
        }
        """;

    @TempDir
    Path tmp;

    private Path trustedFile() throws Exception {
        Path f = tmp.resolve("TrustedKeys.java");
        if (!Files.exists(f)) Files.writeString(f, TRUSTED_TEMPLATE);
        return f;
    }

    @Test
    void keygenCreatesOnceAndNeverOverwrites() throws Exception {
        Path keys = tmp.resolve("keys");
        KeyGenCommand.create(keys, trustedFile());
        List<String> first = TrustedKeysFile.read(trustedFile());
        assertEquals(2, first.size());
        assertEquals(SigningKey.load(keys.resolve("primary.key")).publicBase64(), first.get(0));
        String primaryText = Files.readString(keys.resolve("primary.key"));

        KeyGenCommand.create(keys, trustedFile()); // second run: no change
        assertEquals(first, TrustedKeysFile.read(trustedFile()));
        assertEquals(primaryText, Files.readString(keys.resolve("primary.key")));
        String privateB64 = primaryText.lines().filter(l -> l.startsWith("private=")).findFirst().orElseThrow().substring(8);
        assertFalse(Files.readString(trustedFile()).contains(privateB64), "only public keys go into the mod");
    }

    @Test
    void rotationPromotesBackup() throws Exception {
        Path keys = tmp.resolve("keys");
        KeyGenCommand.create(keys, trustedFile());
        List<String> before = TrustedKeysFile.read(trustedFile());
        KeyGenCommand.rotate(keys, trustedFile());
        List<String> after = TrustedKeysFile.read(trustedFile());
        assertEquals(before.get(1), after.get(0));
        assertNotEquals(before.get(1), after.get(1));
        assertFalse(after.contains(before.get(0)));
        try (Stream<Path> retired = Files.list(keys.resolve("retired"))) {
            assertEquals(1, retired.count());
        }
    }

    // --- Site building ---

    private Path cosmetics(String id) throws Exception {
        Path dir = tmp.resolve("cosmetics").resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("cosmetic.json"),
            "{\"name\":{\"en_us\":\"Top hat\",\"es_es\":\"Chistera\"},\"category\":\"HEAD\",\"rarity\":\"RARE\"}");
        Files.writeString(dir.resolve("model.bbmodel"), BbmodelConverterTest.bbmodel(
            BbmodelConverterTest.cube("u1", "-4,32,-4", "4,40,4", ""),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\"]}", ""));
        Files.write(dir.resolve("texture.png"), PngAndJsonTest.imageIoPng(64, 64, BufferedImage.TYPE_INT_ARGB));
        return dir;
    }

    private SiteBuilder.Options options(SigningKey key, List<String> trusted, long now) {
        return new SiteBuilder.Options(tmp.resolve("cosmetics"), tmp.resolve("site"), key, trusted, now, 30);
    }

    @Test
    void buildsSignedSiteThatTheModAccepts() throws Exception {
        cosmetics("top_hat");
        SigningKey key = SigningKey.generate();
        List<String> trusted = List.of(key.publicBase64());

        SiteBuilder.Summary s = SiteBuilder.build(options(key, trusted, NOW));
        assertEquals(1, s.cosmetics());
        assertEquals(List.of(), SiteVerifier.verify(tmp.resolve("site"), trusted, NOW));

        // Asset file names are their SHA-256: no names controlled by anyone else.
        try (Stream<Path> files = Files.list(tmp.resolve("site/a"))) {
            for (Path f : files.toList()) {
                assertTrue(Hashes.isSha256Hex(f.getFileName().toString()));
                assertTrue(Hashes.matches(Files.readAllBytes(f), f.getFileName().toString()));
            }
        }

        // Versions always go up, even when rebuilt in the same second.
        byte[] firstManifest = Files.readAllBytes(tmp.resolve("site/manifest.json"));
        SiteBuilder.Summary s2 = SiteBuilder.build(options(key, trusted, NOW));
        assertTrue(s2.manifestVersion() > s.manifestVersion());

        // Output is deterministic: same inputs give the same asset hashes (no needless re-downloads).
        Manifest a = ManifestParser.parseUnverified(firstManifest);
        Manifest b = ManifestParser.parseUnverified(Files.readAllBytes(tmp.resolve("site/manifest.json")));
        assertEquals(a.cosmetics().get(0).model(), b.cosmetics().get(0).model());
        assertEquals(a.cosmetics().get(0).texture(), b.cosmetics().get(0).texture());
        try (Stream<Path> files = Files.list(tmp.resolve("site/a"))) {
            assertEquals(2, files.count(), "unreferenced assets are pruned");
        }
    }

    @Test
    void verifierCatchesTamperingAndWrongKeys() throws Exception {
        cosmetics("top_hat");
        SigningKey key = SigningKey.generate();
        List<String> trusted = List.of(key.publicBase64());
        SiteBuilder.build(options(key, trusted, NOW));
        Path site = tmp.resolve("site");

        // Swapped texture file.
        Manifest m = ManifestParser.parseUnverified(Files.readAllBytes(site.resolve("manifest.json")));
        Path tex = site.resolve("a").resolve(m.cosmetics().get(0).texture().sha256());
        byte[] original = Files.readAllBytes(tex);
        byte[] evil = original.clone();
        evil[evil.length - 20] ^= 1;
        Files.write(tex, evil);
        assertFalse(SiteVerifier.verify(site, trusted, NOW).isEmpty());
        Files.write(tex, original);

        // Edited manifest (e.g. rarity changed by hand after signing).
        Path manifest = site.resolve("manifest.json");
        String text = Files.readString(manifest);
        Files.writeString(manifest, text.replace("RARE", "LEGENDARY"));
        assertFalse(SiteVerifier.verify(site, trusted, NOW).isEmpty());
        Files.writeString(manifest, text);

        // Unknown key / expired.
        assertFalse(SiteVerifier.verify(site, List.of(SigningKey.generate().publicBase64()), NOW).isEmpty());
        assertFalse(SiteVerifier.verify(site, trusted, NOW + 31 * 86_400L).isEmpty());
        assertEquals(List.of(), SiteVerifier.verify(site, trusted, NOW));
    }

    @Test
    void refusesUntrustedKeyAndBadInput() throws Exception {
        cosmetics("top_hat");
        SigningKey key = SigningKey.generate();
        assertThrows(ToolException.class,
            () -> SiteBuilder.build(options(key, List.of(SigningKey.generate().publicBase64()), NOW)));

        List<String> trusted = List.of(key.publicBase64());
        Path bad = cosmetics("Bad-Name");
        assertThrows(ToolException.class, () -> SiteBuilder.build(options(key, trusted, NOW)));
        Files.walk(bad).sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());

        Path hat = tmp.resolve("cosmetics/top_hat");
        Files.writeString(hat.resolve("cosmetic.json"),
            "{\"name\":{\"en_us\":\"§kEvil\"},\"category\":\"HEAD\",\"rarity\":\"RARE\"}", StandardCharsets.UTF_8);
        assertThrows(ToolException.class, () -> SiteBuilder.build(options(key, trusted, NOW)));

        Files.writeString(hat.resolve("cosmetic.json"),
            "{\"name\":{\"en_us\":\"Hat\"},\"category\":\"HEAD\",\"rarity\":\"RARE\",\"anchor\":\"LEFT_LEG\"}");
        assertThrows(ToolException.class, () -> SiteBuilder.build(options(key, trusted, NOW)));

        Files.writeString(hat.resolve("cosmetic.json"), "{\"name\":{\"en_us\":\"Hat\"},\"category\":\"HEAD\",\"rarity\":\"RARE\"}");
        Files.write(hat.resolve("texture.png"), PngAndJsonTest.imageIoPng(48, 64, BufferedImage.TYPE_INT_ARGB));
        assertThrows(ToolException.class, () -> SiteBuilder.build(options(key, trusted, NOW)), "texture size mismatch");

        assertFalse(Files.exists(tmp.resolve("site/manifest.json")), "nothing is published when a cosmetic fails");
    }
    @Test
    void checkCosmeticUsesTheSameRulesWithoutWritingAnything() throws Exception {
        Path hat = cosmetics("top_hat");
        assertTrue(SiteBuilder.checkCosmetic("top_hat", hat).isEmpty());

        Files.writeString(hat.resolve("cosmetic.json"),
            "{\"name\":{\"en_us\":\"Hat\"},\"category\":\"HEAD\",\"rarity\":\"RARE\",\"anchor\":\"LEFT_LEG\"}");
        ToolException e = assertThrows(ToolException.class, () -> SiteBuilder.checkCosmetic("top_hat", hat));
        assertTrue(e.getMessage().contains("anchor LEFT_LEG is not allowed"), e.getMessage());
        assertThrows(ToolException.class, () -> SiteBuilder.checkCosmetic("Bad-Name", hat));
        assertFalse(Files.exists(tmp.resolve("site")), "checking never writes a site");
    }
}
