package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;
import com.heytozzz.htzcosmetics.core.png.DecodedImage;
import com.heytozzz.htzcosmetics.core.png.SafePngDecoder;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PngAndJsonTest {
    static byte[] imageIoPng(int w, int h, int type) throws Exception {
        BufferedImage img = new BufferedImage(w, h, type);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, 0xFF000000 | (x * 4) << 16 | (y * 4) << 8 | 0x40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void normalizesPaletteAndRgbPngsIntoTheOnlyAcceptedLayout() throws Exception {
        for (int type : new int[]{BufferedImage.TYPE_BYTE_INDEXED, BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB}) {
            byte[] png = PngNormalizer.normalize(imageIoPng(32, 16, type), "t");
            DecodedImage img = SafePngDecoder.decode(png);
            assertEquals(32, img.width());
            assertEquals(16, img.height());
        }
    }

    @Test
    void preservesTransparency() throws Exception {
        int[] argb = {0x00000000, 0x80FF0000, 0xFF00FF00, 0x10203040};
        DecodedImage img = SafePngDecoder.decode(PngNormalizer.encodeRgba(2, 2, argb));
        for (int i = 0; i < 4; i++) assertEquals(argb[i], img.argb()[i]);
    }

    @Test
    void rejectsTooBigOrNonImage() throws Exception {
        assertThrows(ToolException.class, () -> PngNormalizer.normalize(imageIoPng(300, 10, BufferedImage.TYPE_INT_ARGB), "t"));
        assertThrows(ToolException.class, () -> PngNormalizer.normalize("hello".getBytes(), "t"));
    }

    @Test
    void jsonWriterOutputIsAcceptedByStrictParser() throws Exception {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", "Chistera é \"q\" \\");
        obj.put("n", 0.1 + 0.2);
        obj.put("neg", -0.0);
        obj.put("big", 1_800_000_000L);
        obj.put("v", new double[]{-1.9, 24, 1e-7});
        obj.put("list", List.of(1, true));
        byte[] bytes = JsonWriter.toBytes(obj);
        for (byte b : bytes) assertTrue(b >= 0, "output must be ASCII");
        Map<String, Object> back = Json.asObject(StrictJsonParser.parse(bytes, 4096), "root");
        assertEquals("Chistera é \"q\" \\", back.get("name"));
        assertEquals(0.3, back.get("n"));
        assertEquals(0.0, back.get("neg"));
        assertEquals(1.8e9, back.get("big"));
        assertEquals(List.of(-1.9, 24.0, 0.0), back.get("v"));
    }
}
