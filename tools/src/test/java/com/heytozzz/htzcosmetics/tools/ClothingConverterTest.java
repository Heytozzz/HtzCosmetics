package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.heytozzz.htzcosmetics.tools.BbmodelConverterTest.bbmodel;
import static com.heytozzz.htzcosmetics.tools.BbmodelConverterTest.cube;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Top-level groups named after body parts become pieces that follow those parts. */
class ClothingConverterTest {
    // A shirt modelled on a standard player: torso around the body, one sleeve around the left arm.
    private static final String SHIRT = bbmodel(
        cube("t", "-4,12,-2", "4,24,2", "") + "," + cube("s", "4,18,-2", "8,24,2", ""),
        "{\"name\":\"Body\",\"origin\":[0,24,0],\"uuid\":\"g1\",\"children\":[\"t\"]},"
            + "{\"name\":\"brazo izquierdo\",\"origin\":[-5,22,0],\"uuid\":\"g2\",\"children\":[\"s\"]}",
        "");

    private static CosmeticModel convert(Category category) throws Exception {
        BbmodelConverter.Result r = BbmodelConverter.convert(SHIRT.getBytes(StandardCharsets.UTF_8), category,
            category.defaultAnchor(), "t");
        return ModelParser.parse(r.modelJson());
    }

    @Test
    void groupsNamedAfterBodyPartsFollowThem() throws Exception {
        CosmeticModel m = convert(Category.SHIRT);
        assertEquals(2, m.format());
        assertNull(m.bones().get(0).anchor());                     // "Body" is the shirt's own anchor
        assertEquals(Anchor.LEFT_ARM, m.bones().get(1).anchor());  // Spanish name works too
        // The sleeve's group pivot sits exactly on the left arm's pivot, so it is relative to the arm.
        assertArrayEquals(new double[]{0, 0, 0}, m.bones().get(1).pivot());
        ModelParser.checkAnchors(m, Category.SHIRT);
    }

    @Test
    void partsTheCategoryDoesNotAllowStayNormalGroups() throws Exception {
        CosmeticModel m = convert(Category.BACK); // backpacks cannot use the arms
        assertEquals(1, m.format());
        assertNull(m.bones().get(1).anchor());
    }
}
