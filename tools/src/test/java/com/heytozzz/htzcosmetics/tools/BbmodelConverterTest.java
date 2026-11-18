package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.model.Bone;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.Cube;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbmodelConverterTest {
    static String cube(String uuid, String from, String to, String extra) {
        return "{\"name\":\"c" + uuid + "\",\"type\":\"cube\",\"from\":[" + from + "],\"to\":[" + to + "],"
            + "\"origin\":[0,24,0],\"uv_offset\":[0,0],\"uuid\":\"" + uuid + "\"" + extra + "}";
    }

    static String bbmodel(String elements, String outliner, String extra) {
        return "{\"meta\":{\"format_version\":\"4.10\",\"box_uv\":true},\"resolution\":{\"width\":64,\"height\":64},"
            + "\"elements\":[" + elements + "],\"outliner\":[" + outliner + "]" + extra + "}";
    }

    private static CosmeticModel convertAndParse(String bb, Anchor anchor) throws Exception {
        BbmodelConverter.Result r = BbmodelConverter.convert(bb.getBytes(StandardCharsets.UTF_8), anchor, "test");
        return ModelParser.parse(r.modelJson());
    }

    @Test
    void convertsHatOnHeadToHeadSpace() throws Exception {
        String bb = bbmodel(cube("u1", "-4,32,-4", "4,40,4", ""),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\"]}", "");
        CosmeticModel m = convertAndParse(bb, Anchor.HEAD);
        Bone hat = m.bones().get(0);
        assertArrayEquals(new double[]{0, 0, 0}, hat.pivot());
        Cube c = hat.cubes().get(0);
        // Same numbers vanilla uses for a head-sized box above the head: Y is negative (up) in Minecraft space.
        assertArrayEquals(new double[]{-4, -16, -4}, c.origin());
        assertArrayEquals(new double[]{8, 8, 8}, c.size());
    }

    @Test
    void nestedGroupsUseParentRelativePivotsAndNegatedRotation() throws Exception {
        String bb = bbmodel(cube("u1", "-1,30,4", "1,34,6", ",\"mirror_uv\":true,\"inflate\":0.25"),
            "{\"name\":\"wings\",\"origin\":[0,24,0],\"children\":[{\"name\":\"left\",\"origin\":[2,28,5],"
                + "\"rotation\":[10,20,30],\"children\":[\"u1\"]}]}", "");
        CosmeticModel m = convertAndParse(bb, Anchor.BODY);
        Bone left = m.bones().get(1);
        assertEquals(0, left.parentIndex());
        assertArrayEquals(new double[]{-2, -4, 5}, left.pivot());
        assertArrayEquals(new double[]{-10, -20, 30}, left.rotation());
        Cube c = left.cubes().get(0);
        assertArrayEquals(new double[]{-(1 - 2), -(34 - 28), 4 - 5}, c.origin());
        assertTrue(c.mirror());
        assertEquals(0.25, c.inflate());
    }

    @Test
    void rotatedCubeBecomesItsOwnBone() throws Exception {
        String bb = bbmodel(cube("u1", "-1,32,-1", "1,34,1", ",\"rotation\":[0,45,0]"),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\"]}", "");
        CosmeticModel m = convertAndParse(bb, Anchor.HEAD);
        assertEquals(2, m.bones().size());
        assertEquals(-45.0, m.bones().get(1).rotation()[1]);
        assertTrue(m.bones().get(0).cubes().isEmpty());
    }

    @Test
    void supportsBlockbench5GroupsAndLooseCubes() throws Exception {
        String bb = "{\"meta\":{\"box_uv\":true},\"resolution\":{\"width\":64,\"height\":64},"
            + "\"elements\":[" + cube("u1", "-4,32,-4", "4,33,4", "") + "," + cube("u2", "0,24,0", "1,25,1", "") + "],"
            + "\"groups\":[{\"name\":\"hat\",\"origin\":[0,24,0],\"uuid\":\"g1\"}],"
            + "\"outliner\":[{\"uuid\":\"g1\",\"isOpen\":true,\"children\":[\"u1\"]},\"u2\"]}";
        CosmeticModel m = convertAndParse(bb, Anchor.HEAD);
        assertEquals("hat", m.bones().get(0).name());
        assertEquals("root", m.bones().get(1).name());
        assertEquals(2, m.cubeCount());
    }

    @Test
    void skipsHiddenReferenceParts() throws Exception {
        String bb = bbmodel(cube("u1", "-4,32,-4", "4,33,4", "") + "," + cube("u2", "-4,24,-4", "4,32,4", ",\"visibility\":false"),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\",\"u2\"]}", "");
        BbmodelConverter.Result r = BbmodelConverter.convert(bb.getBytes(StandardCharsets.UTF_8), Anchor.HEAD, "t");
        assertEquals(1, ModelParser.parse(r.modelJson()).cubeCount());
        assertFalse(r.warnings().isEmpty());
    }

    @Test
    void ignoresAnimationsWithMolangAndWarns() throws Exception {
        String bb = bbmodel(cube("u1", "-4,32,-4", "4,33,4", ""),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\"]}",
            ",\"animations\":[{\"name\":\"spin\",\"animators\":{\"x\":{\"keyframes\":[{\"data_points\":[{\"y\":\"query.anim_time*90\"}]}]}}}]");
        BbmodelConverter.Result r = BbmodelConverter.convert(bb.getBytes(StandardCharsets.UTF_8), Anchor.HEAD, "t");
        String json = new String(r.modelJson(), StandardCharsets.US_ASCII);
        assertFalse(json.contains("query"), "Molang must never reach the output");
        assertFalse(json.contains("animation"));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("animations")));
    }

    @Test
    void rejectsMeshesAndPerFaceUv() {
        String mesh = bbmodel("{\"name\":\"m\",\"type\":\"mesh\",\"uuid\":\"u1\"}",
            "{\"name\":\"g\",\"origin\":[0,24,0],\"children\":[\"u1\"]}", "");
        assertThrows(ToolException.class, () -> BbmodelConverter.convert(mesh.getBytes(), Anchor.HEAD, "t"));
        String perFace = bbmodel(cube("u1", "0,0,0", "1,1,1", ",\"box_uv\":false"),
            "{\"name\":\"g\",\"origin\":[0,24,0],\"children\":[\"u1\"]}", "");
        assertThrows(ToolException.class, () -> BbmodelConverter.convert(perFace.getBytes(), Anchor.HEAD, "t"));
        assertThrows(ToolException.class, () -> BbmodelConverter.convert("not json".getBytes(), Anchor.HEAD, "t"));
    }

    @Test
    void sanitizesBoneNames() throws Exception {
        String bb = bbmodel(cube("u1", "-4,32,-4", "4,33,4", "") + "," + cube("u2", "-4,33,-4", "4,34,4", ""),
            "{\"name\":\"../evil name\",\"origin\":[0,24,0],\"children\":[\"u1\"]},"
                + "{\"name\":\"../evil name\",\"origin\":[0,24,0],\"children\":[\"u2\"]}", "");
        CosmeticModel m = convertAndParse(bb, Anchor.HEAD);
        assertEquals("___evil_name", m.bones().get(0).name());
        assertEquals("___evil_name_2", m.bones().get(1).name());
    }

    @Test
    void extractsEmbeddedPngTexture() {
        String bb = bbmodel(cube("u1", "-4,32,-4", "4,33,4", ""),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\"]}",
            ",\"textures\":[{\"source\":\"data:image/png;base64,iVBORw0KGgo=\"}]");
        byte[] tex = BbmodelConverter.convert(bb.getBytes(), Anchor.HEAD, "t").embeddedTexture();
        assertEquals((byte) 0x89, tex[0]);
        String none = bbmodel(cube("u1", "-4,32,-4", "4,33,4", ""),
            "{\"name\":\"hat\",\"origin\":[0,24,0],\"children\":[\"u1\"]}", ",\"textures\":[{\"path\":\"C:/x.png\"}]");
        assertNull(BbmodelConverter.convert(none.getBytes(), Anchor.HEAD, "t").embeddedTexture());
    }
}
