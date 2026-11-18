package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.heytozzz.htzcosmetics.core.manifest.ManifestParserTest.cosmetic;
import static com.heytozzz.htzcosmetics.core.manifest.ManifestParserTest.manifest;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Content format 2: clothing categories, per-group body parts, glow and auras. */
class NewCosmeticTypesTest {
    private static final long NOW = 1_800_000_000L;
    private static final String GLOW = ",\"glow\":{\"colors\":[\"#FFD24A\",\"#ff5ad2\"],\"cycle\":3,\"bloom\":0.8,"
        + "\"radius\":1.2,\"pulse\":2}";

    private static String aura(String id, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":{\"en_us\":\"Aura\"},\"category\":\"AURA\",\"rarity\":\"EPIC\","
            + "\"anchor\":\"BODY\",\"minFormat\":2" + extra + "}";
    }

    private static Manifest parse(String... cosmetics) throws ValidationException {
        return ManifestParser.parseUnverified(manifest(1, 1, NOW + 1000, cosmetics));
    }

    @Test
    void parsesClothingGlowAndAura() throws Exception {
        Manifest m = parse(
            cosmetic("shirt", "SHIRT", "BODY", "COMMON", 2, ""),
            cosmetic("shoes", "SHOES", "RIGHT_LEG", "COMMON", 2, ""),
            cosmetic("crown", "HEAD", "HEAD", "LEGENDARY", 2, GLOW),
            aura("aura", GLOW + ",\"aura\":{\"fill\":0.3,\"outline\":0.6}"));
        assertEquals(4, m.cosmetics().size());
        Glow glow = m.byId().get("crown").glow();
        assertArrayEquals(new int[]{0xFFD24A, 0xFF5AD2}, glow.colors());
        assertTrue(glow.emissive()); // default
        CosmeticDefinition aura = m.byId().get("aura");
        assertFalse(aura.hasModel());
        assertNull(aura.texture());
        assertEquals(0.6, aura.aura().outline());
        assertNotNull(aura.glow());
        assertEquals(0.9, aura.aura().outlineStrength()); // default
        // A missing "aura" block gets the defaults: glowing edge on, no light over the body.
        CosmeticDefinition plain = parse(aura("plain", GLOW)).byId().get("plain");
        assertEquals(Aura.DEFAULT, plain.aura());
        assertEquals(8.0, Glow.MAX_RADIUS);
    }

    @Test
    void newContentMustDeclareFormat2SoOlderBuildsSkipIt() throws Exception {
        Manifest m = parse(
            cosmetic("shirt", "SHIRT", "BODY", "COMMON", 1, ""),          // new category, minFormat 1
            cosmetic("crown", "HEAD", "HEAD", "RARE", 1, GLOW),          // glow, minFormat 1
            aura("aura", GLOW).replace("\"minFormat\":2", "\"minFormat\":1"));
        assertEquals(0, m.cosmetics().size());
        assertEquals(3, m.skippedEntries());
    }

    @Test
    void rejectsBadGlowAndAuraValues() throws Exception {
        Manifest m = parse(
            cosmetic("a", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":[]}"),                     // no colour
            cosmetic("b", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":[\"red\"]}"),              // not #RRGGBB
            cosmetic("c", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":[\"#FFFFFF\"],\"bloom\":5}"),
            cosmetic("d", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":[\"#FFFFFF\"],\"radius\":40}"),
            cosmetic("e", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":[\"#FFFFFF\"],\"pulse\":0.01}"),
            cosmetic("f", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":["
                + "\"#000000\",".repeat(8) + "\"#000000\"]}"),                                         // 9 colours
            cosmetic("g", "HEAD", "HEAD", "RARE", 2, ",\"glow\":{\"colors\":[\"#FFFFFF\"],\"cycle\":-1}"),
            cosmetic("h", "HEAD", "HEAD", "RARE", 2, ",\"aura\":{}"),                                  // aura on a hat
            aura("i", ""),                                                                            // aura without glow
            aura("j", GLOW + ",\"aura\":{\"outline\":99}"),
            aura("l", GLOW + ",\"aura\":{\"outlineStrength\":2}"),
            aura("k", GLOW + ",\"model\":{\"sha256\":\"" + "a".repeat(64) + "\",\"size\":10}"));       // model, no texture
        assertEquals(0, m.cosmetics().size());
        assertEquals(12, m.skippedEntries());
    }

    @Test
    void glowColoursBlendInALoop() throws Exception {
        Glow g = Glow.parse(Json.asObject(StrictJsonParser.parse(
            "{\"colors\":[\"#FF0000\",\"#0000FF\"],\"cycle\":2}".getBytes(StandardCharsets.UTF_8), 1000), "g"));
        assertEquals(0xFF0000, g.colorAt(0));
        assertEquals(0x0000FF, g.colorAt(1));
        assertEquals(0xFF0000, g.colorAt(2));
        assertEquals(0x800080, g.colorAt(0.5));
        assertEquals(1.0, g.pulseAt(123));
    }

    @Test
    void parsesTintAndDecidesWhichColoursAreAllowed() throws Exception {
        Manifest m = parse(cosmetic("shirt", "SHIRT", "BODY", "COMMON", 2,
            ",\"tint\":{\"default\":\"#D03040\",\"palette\":[\"#3070FF\"],\"free\":false}"));
        Tint t = m.byId().get("shirt").tint();
        assertEquals(0xD03040, t.defaultColor());
        assertTrue(t.allows(0x3070FF));
        assertFalse(t.allows(0x123456));                // not offered and no colour wheel
        assertEquals(0xD03040, t.effective(0x123456));  // a forbidden request falls back to the default
        assertEquals(0xD03040, t.effective(null));
        assertTrue(t.glow());                           // default
        Tint free = new Tint(0, new int[0], true, true);
        assertTrue(free.allows(0x123456));
    }

    @Test
    void rejectsBadTints() throws Exception {
        Manifest m = parse(
            cosmetic("a", "HEAD", "HEAD", "RARE", 2, ",\"tint\":{}"),                                   // no default
            cosmetic("b", "HEAD", "HEAD", "RARE", 2, ",\"tint\":{\"default\":\"red\"}"),
            cosmetic("c", "HEAD", "HEAD", "RARE", 2, ",\"tint\":{\"default\":\"#FFFFFF\",\"palette\":["
                + "\"#000000\",".repeat(16) + "\"#000000\"]}"),                                        // 17 squares
            cosmetic("d", "HEAD", "HEAD", "RARE", 1, ",\"tint\":{\"default\":\"#FFFFFF\"}"));         // minFormat 1
        assertEquals(0, m.cosmetics().size());
        assertEquals(4, m.skippedEntries());
    }

    @Test
    void tintColoursOnlyGreyPixels() {
        assertEquals(0xFF3070FF, Tint.apply(0xFFFFFFFF, 0x3070FF));  // white takes the colour
        assertEquals(0xFF183880, Tint.apply(0xFF808080, 0x3070FF));  // grey: darker shade of it
        assertEquals(0xFF000000, Tint.apply(0xFF000000, 0x3070FF));  // black stays black
        assertEquals(0xFF6E4628, Tint.apply(0xFF6E4628, 0x3070FF));  // coloured pixels stay
        assertEquals(0x00FFFFFF, Tint.apply(0x00FFFFFF, 0x3070FF));  // transparent stays
    }

    @Test
    void equippedEntriesCarryAnOptionalColour() {
        var plain = com.heytozzz.htzcosmetics.core.net.EquippedEntry.parse("top_hat");
        assertEquals("top_hat", plain.id());
        assertNull(plain.color());
        var coloured = com.heytozzz.htzcosmetics.core.net.EquippedEntry.parse("top_hat:3070ff");
        assertEquals(0x3070FF, coloured.color());
        assertEquals("top_hat:3070ff", coloured.format());
        for (String bad : new String[]{"top_hat:3070FF", "top_hat:3070f", "top_hat:", "../x", "a:b:c", "x".repeat(33),
            "top_hat:#3070ff", ""}) {
            assertNull(com.heytozzz.htzcosmetics.core.net.EquippedEntry.parse(bad), bad);
        }
    }

    private static CosmeticModel model(String bones) throws ValidationException {
        return ModelParser.parse(("{\"format\":2,\"texture\":{\"width\":64,\"height\":64},\"bones\":[" + bones + "]}")
            .getBytes(StandardCharsets.UTF_8));
    }

    private static final String CUBE = "{\"origin\":[-4,0,-2],\"size\":[8,12,4],\"uv\":[0,0]}";

    @Test
    void modelGroupsCanFollowOtherBodyParts() throws Exception {
        CosmeticModel m = model("{\"name\":\"torso\",\"cubes\":[" + CUBE + "]},"
            + "{\"name\":\"sleeve\",\"anchor\":\"LEFT_ARM\",\"cubes\":[" + CUBE + "]}");
        assertNull(m.bones().get(0).anchor());
        assertEquals(Anchor.LEFT_ARM, m.bones().get(1).anchor());
        ModelParser.checkAnchors(m, Category.SHIRT);
        assertThrows(ValidationException.class, () -> ModelParser.checkAnchors(m, Category.HEAD)); // no sleeves on hats
        assertThrows(ValidationException.class, () -> ModelParser.checkAnchors(m, Category.PANTS));
    }

    @Test
    void rejectsBadBoneAnchors() {
        assertThrows(ValidationException.class, () -> model(
            "{\"name\":\"a\",\"anchor\":\"TAIL\",\"cubes\":[" + CUBE + "]}"));                       // unknown part
        assertThrows(ValidationException.class, () -> model(
            "{\"name\":\"a\",\"cubes\":[" + CUBE + "]},{\"name\":\"b\",\"parent\":\"a\",\"anchor\":\"HEAD\"}")); // child
        assertThrows(ValidationException.class, () -> ModelParser.parse(
            ("{\"format\":1,\"texture\":{\"width\":64,\"height\":64},\"bones\":[{\"name\":\"a\",\"anchor\":\"HEAD\","
                + "\"cubes\":[" + CUBE + "]}]}").getBytes(StandardCharsets.UTF_8)));                     // format 1
    }
}
