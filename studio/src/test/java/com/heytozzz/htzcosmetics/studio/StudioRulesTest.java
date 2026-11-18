package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioRulesTest {
    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(p.resolve("cosmetics"))) p = p.getParent();
        return p;
    }

    @Test
    void toolMessagesBecomePlainSpanish() {
        assertEquals("Falta el archivo cosmetic.json.", ErrorText.translate("cosmetics/hat: missing cosmetic.json"));
        assertTrue(ErrorText.translate("[htzBuildSite] ERROR: cosmetics/hat: anchor LEFT_LEG is not allowed for category HEAD")
            .startsWith("El anclaje LEFT_LEG no está permitido en la categoría HEAD"));
        assertTrue(ErrorText.translate("cosmetics/hat: texture is 48x64 but the model expects 64x64 (or an exact multiple)")
            .startsWith("La textura mide 48×64 pero el modelo espera 64×64"));
        assertTrue(ErrorText.translate("cosmetics/hat: animation 'idle' (rotation) uses the expression \"math.sin(1)\"")
            .contains("Molang"));
        assertTrue(ErrorText.translate("something new").startsWith("Detalle técnico (en inglés): something new"));
        assertEquals("hat", ErrorText.cosmeticOf("[htzBuildSite] ERROR: cosmetics/hat: missing cosmetic.json"));
    }

    @Test
    void namesUseTheModRules() {
        assertNull(EditorDialog.nameProblem("en_us", "Top hat"));
        assertNull(EditorDialog.nameProblem("es_es", "Sombrero de copa ñ"));
        assertNull(EditorDialog.nameProblem("es_mx", ""));
        assertNotNull(EditorDialog.nameProblem("en_us", ""));
        assertNotNull(EditorDialog.nameProblem("en_us", "§kEvil"));
        assertNotNull(EditorDialog.nameProblem("es_es", "a\u202Eb"));
        assertNotNull(EditorDialog.nameProblem("en_us", "x".repeat(49)));
    }

    @Test
    void realCosmeticsPassAndRender() throws Exception {
        Path cosmetics = root().resolve("cosmetics");
        List<CosmeticStore.Entry> all = CosmeticStore.loadAll(cosmetics);
        assertTrue(all.size() >= 2);
        for (CosmeticStore.Entry e : all) {
            assertNull(e.problem(), e.id());
            assertEquals(List.of(), CosmeticStore.check(e.id(), e.folder()), e.id());
        }
        CosmeticStore.Loaded hat = CosmeticStore.loadFolderModel(cosmetics.resolve("top_hat"), Anchor.HEAD);
        BufferedImage img = ModelView.thumbnail(hat, 128);
        int drawn = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) if ((img.getRGB(x, y) >>> 24) != 0) drawn++;
        assertTrue(drawn > 128 * 128 / 10, "thumbnail draws the hat (" + drawn + " pixels)");
        assertTrue(CosmeticStore.loadAll(cosmetics).stream().anyMatch(CosmeticStore.Entry::animated), "angel_halo is animated");
    }
}
