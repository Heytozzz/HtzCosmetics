package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.png.DecodedImage;
import com.heytozzz.htzcosmetics.core.png.SafePngDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Re-encodes any PNG the owner made into the ONLY layout the mod accepts:
 * 8-bit RGBA, non-interlaced, just IHDR + IDAT + IEND (all metadata stripped).
 *
 * <p>This runs on the owner's PC on the owner's own files, so using Java's full image reader here is fine;
 * the mod itself only ever uses {@link SafePngDecoder}.
 */
public final class PngNormalizer {
    private PngNormalizer() {
    }

    public static byte[] normalize(byte[] anyPng, String label) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(anyPng));
        } catch (IOException e) {
            throw new ToolException(label + ": cannot read image (" + e.getMessage() + ")");
        }
        if (image == null) throw new ToolException(label + ": not a readable PNG image");
        int w = image.getWidth();
        int h = image.getHeight();
        if (w > Limits.MAX_TEXTURE_DIMENSION || h > Limits.MAX_TEXTURE_DIMENSION) {
            throw new ToolException(label + ": texture is " + w + "x" + h + ", maximum is "
                + Limits.MAX_TEXTURE_DIMENSION + "x" + Limits.MAX_TEXTURE_DIMENSION);
        }
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        byte[] png = encodeRgba(w, h, argb);
        if (png.length > Limits.MAX_TEXTURE_BYTES) {
            throw new ToolException(label + ": texture is still " + png.length / 1024 + " KB after compression (max "
                + Limits.MAX_TEXTURE_BYTES / 1024 + " KB)");
        }
        // Prove the mod will accept it, using the mod's own decoder.
        try {
            DecodedImage check = SafePngDecoder.decode(png);
            for (int i = 0; i < argb.length; i++) {
                if (check.argb()[i] != argb[i]) throw new ToolException(label + ": PNG round-trip mismatch (bug)");
            }
        } catch (ValidationException e) {
            throw new ToolException(label + ": encoded PNG rejected by the mod decoder (bug): " + e.getMessage());
        }
        return png;
    }

    /** Encodes ARGB pixels as an 8-bit RGBA PNG, choosing the smallest filter per row. */
    public static byte[] encodeRgba(int width, int height, int[] argb) {
        int stride = width * 4;
        byte[] prev = new byte[stride];
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < height; y++) {
            byte[] row = new byte[stride];
            for (int x = 0; x < width; x++) {
                int p = argb[y * width + x];
                row[x * 4] = (byte) (p >>> 16);
                row[x * 4 + 1] = (byte) (p >>> 8);
                row[x * 4 + 2] = (byte) p;
                row[x * 4 + 3] = (byte) (p >>> 24);
            }
            byte[] best = null;
            long bestScore = Long.MAX_VALUE;
            for (int type = 0; type <= 4; type++) {
                byte[] filtered = filter(type, row, prev);
                long score = 0;
                for (int i = 1; i < filtered.length; i++) score += Math.abs((int) filtered[i]);
                if (score < bestScore) {
                    bestScore = score;
                    best = filtered;
                }
            }
            raw.writeBytes(best);
            prev = row;
        }

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf));
        deflater.end();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, width);
        putInt(ihdr, 4, height);
        ihdr[8] = 8;  // bit depth
        ihdr[9] = 6;  // color type RGBA
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] filter(int type, byte[] row, byte[] prev) {
        byte[] out = new byte[row.length + 1];
        out[0] = (byte) type;
        for (int i = 0; i < row.length; i++) {
            int x = row[i] & 0xFF;
            int a = i >= 4 ? row[i - 4] & 0xFF : 0;
            int b = prev[i] & 0xFF;
            int c = i >= 4 ? prev[i - 4] & 0xFF : 0;
            int predictor = switch (type) {
                case 0 -> 0;
                case 1 -> a;
                case 2 -> b;
                case 3 -> (a + b) >>> 1;
                default -> paeth(a, b, c);
            };
            out[i + 1] = (byte) (x - predictor);
        }
        return out;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] len = new byte[4];
        putInt(len, 0, data.length);
        out.writeBytes(len);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        byte[] c = new byte[4];
        putInt(c, 0, (int) crc.getValue());
        out.writeBytes(c);
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }
}
