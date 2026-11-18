package com.heytozzz.htzcosmetics.core.png;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Builds valid and deliberately broken PNG files for tests. */
final class TestPng {
    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    TestPng() {
        out.writeBytes(SIGNATURE);
    }

    static byte[] ihdr(long width, long height, int bitDepth, int colorType, int interlace) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeInt(b, width);
        writeInt(b, height);
        b.write(bitDepth);
        b.write(colorType);
        b.write(0);
        b.write(0);
        b.write(interlace);
        return b.toByteArray();
    }

    TestPng chunk(String type, byte[] data) {
        return chunk(type, data, true);
    }

    TestPng chunk(String type, byte[] data, boolean correctCrc) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeInt(out, data.length);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, correctCrc ? crc.getValue() : crc.getValue() ^ 1);
        return this;
    }

    TestPng raw(byte[] bytes) {
        out.writeBytes(bytes);
        return this;
    }

    byte[] bytes() {
        return out.toByteArray();
    }

    /** Raw scanlines (filter byte + RGBA) for the given pixels, all rows using {@code filter}. */
    static byte[] scanlines(int width, int height, int[] argb, int filter) {
        int stride = width * 4;
        byte[] raw = new byte[height * (stride + 1)];
        byte[] prev = new byte[stride];
        for (int y = 0; y < height; y++) {
            byte[] cur = new byte[stride];
            for (int x = 0; x < width; x++) {
                int p = argb[y * width + x];
                cur[x * 4] = (byte) (p >> 16);
                cur[x * 4 + 1] = (byte) (p >> 8);
                cur[x * 4 + 2] = (byte) p;
                cur[x * 4 + 3] = (byte) (p >>> 24);
            }
            int o = y * (stride + 1);
            raw[o] = (byte) filter;
            for (int i = 0; i < stride; i++) {
                int a = i >= 4 ? cur[i - 4] & 0xFF : 0;
                int b = prev[i] & 0xFF;
                int c = i >= 4 ? prev[i - 4] & 0xFF : 0;
                int predictor;
                switch (filter) {
                    case 0: predictor = 0; break;
                    case 1: predictor = a; break;
                    case 2: predictor = b; break;
                    case 3: predictor = (a + b) >>> 1; break;
                    case 4: predictor = paeth(a, b, c); break;
                    default: throw new IllegalArgumentException();
                }
                raw[o + 1 + i] = (byte) ((cur[i] & 0xFF) - predictor);
            }
            prev = cur;
        }
        return raw;
    }

    static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) b.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return b.toByteArray();
    }

    /** A complete, valid RGBA PNG. */
    static byte[] valid(int width, int height, int[] argb, int filter) {
        return new TestPng()
            .chunk("IHDR", ihdr(width, height, 8, 6, 0))
            .chunk("tEXt", "Comment\0hello".getBytes(StandardCharsets.ISO_8859_1))
            .chunk("IDAT", deflate(scanlines(width, height, argb, filter)))
            .chunk("IEND", new byte[0])
            .bytes();
    }

    static int[] pattern(int width, int height) {
        int[] px = new int[width * height];
        for (int i = 0; i < px.length; i++) px[i] = (int) (i * 2654435761L) ^ (i << 24); // varied ARGB values
        return px;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }

    private static void writeInt(ByteArrayOutputStream b, long v) {
        b.write((int) (v >>> 24));
        b.write((int) (v >>> 16));
        b.write((int) (v >>> 8));
        b.write((int) v);
    }
}
