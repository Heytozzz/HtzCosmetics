package com.heytozzz.htzcosmetics.core.png;

import com.heytozzz.htzcosmetics.core.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafePngDecoderTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void decodesEveryFilterType(int filter) throws Exception {
        int[] pixels = TestPng.pattern(16, 8);
        DecodedImage img = SafePngDecoder.decode(TestPng.valid(16, 8, pixels, filter));
        assertEquals(16, img.width());
        assertEquals(8, img.height());
        assertArrayEquals(pixels, img.argb());
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(null));
    }

    @Test
    void rejectsZipDisguisedAsPng() {
        byte[] zip = new byte[200];
        zip[0] = 'P';
        zip[1] = 'K';
        zip[2] = 3;
        zip[3] = 4;
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(zip));
    }

    @Test
    void rejectsScriptDisguisedAsPng() {
        byte[] script = "<script>alert(1)</script> #!/bin/sh rm -rf / ...................".getBytes(StandardCharsets.UTF_8);
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(script));
    }

    @Test
    void rejectsTruncatedFile() {
        byte[] png = TestPng.valid(16, 16, TestPng.pattern(16, 16), 0);
        for (int cut : new int[]{png.length - 1, png.length - 12, png.length / 2, 40}) {
            byte[] truncated = Arrays.copyOf(png, cut);
            assertThrows(ValidationException.class, () -> SafePngDecoder.decode(truncated), "cut at " + cut);
        }
    }

    @Test
    void rejectsBadCrc() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(4, 4, 8, 6, 0), false)
            .chunk("IDAT", TestPng.deflate(TestPng.scanlines(4, 4, new int[16], 0)))
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsHugeDimensionsBeforeDecompressing() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(100_000, 100_000, 8, 6, 0))
            .chunk("IDAT", TestPng.deflate(new byte[10]))
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsMaxUnsignedDimensions() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(0xFFFFFFFFL, 1, 8, 6, 0))
            .chunk("IDAT", TestPng.deflate(new byte[10]))
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsDecompressionBomb() {
        // Header says 16x16, but the compressed data expands to 50 MB of zeros (~50 KB compressed).
        byte[] bomb = TestPng.deflate(new byte[50 * 1024 * 1024]);
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(16, 16, 8, 6, 0))
            .chunk("IDAT", bomb)
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsTooLittleImageData() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(16, 16, 8, 6, 0))
            .chunk("IDAT", TestPng.deflate(new byte[20]))
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsNonRgbaAndInterlacedAndOtherDepths() {
        int[][] headers = {{8, 2, 0}, {8, 3, 0}, {16, 6, 0}, {8, 0, 0}, {8, 6, 1}};
        for (int[] h : headers) {
            byte[] png = new TestPng()
                .chunk("IHDR", TestPng.ihdr(4, 4, h[0], h[1], h[2]))
                .chunk("IDAT", TestPng.deflate(TestPng.scanlines(4, 4, new int[16], 0)))
                .chunk("IEND", new byte[0])
                .bytes();
            assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png), Arrays.toString(h));
        }
    }

    @Test
    void rejectsUnknownCriticalChunk() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(4, 4, 8, 6, 0))
            .chunk("PLTE", new byte[3])
            .chunk("IDAT", TestPng.deflate(TestPng.scanlines(4, 4, new int[16], 0)))
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsDataAfterIend() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(4, 4, 8, 6, 0))
            .chunk("IDAT", TestPng.deflate(TestPng.scanlines(4, 4, new int[16], 0)))
            .chunk("IEND", new byte[0])
            .raw("PK\u0003\u0004 hidden payload".getBytes(StandardCharsets.ISO_8859_1))
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsMissingIend() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(4, 4, 8, 6, 0))
            .chunk("IDAT", TestPng.deflate(TestPng.scanlines(4, 4, new int[16], 0)))
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsInvalidFilterByte() {
        byte[] raw = TestPng.scanlines(4, 4, new int[16], 0);
        raw[0] = 7;
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(4, 4, 8, 6, 0))
            .chunk("IDAT", TestPng.deflate(raw))
            .chunk("IEND", new byte[0])
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsChunkLengthPointingOutsideFile() {
        byte[] png = new TestPng()
            .chunk("IHDR", TestPng.ihdr(4, 4, 8, 6, 0))
            .raw(new byte[]{0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 'I', 'D', 'A', 'T', 0, 0, 0, 0})
            .bytes();
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(png));
    }

    @Test
    void rejectsOversizedFile() {
        byte[] big = Arrays.copyOf(TestPng.valid(4, 4, new int[16], 0), 300 * 1024);
        assertThrows(ValidationException.class, () -> SafePngDecoder.decode(big));
    }
}
