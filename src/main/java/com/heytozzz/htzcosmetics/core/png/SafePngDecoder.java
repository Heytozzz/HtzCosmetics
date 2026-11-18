package com.heytozzz.htzcosmetics.core.png;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Pure-Java PNG decoder for untrusted textures.
 *
 * <p>Deliberately tiny: it accepts ONLY 8-bit RGBA, non-interlaced PNGs (the offline signing tool
 * converts every texture to exactly that). It does not use Minecraft's native image loader.
 *
 * <p>Defences: magic bytes, CRC of every chunk, dimension limits checked before decompression,
 * decompression output capped to the exact expected size (no decompression bombs), unknown critical
 * chunks rejected, no data allowed after IEND.
 */
public final class SafePngDecoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int COLOR_TYPE_RGBA = 6;
    private static final int BYTES_PER_PIXEL = 4;

    private SafePngDecoder() {
    }

    public static DecodedImage decode(byte[] data) throws ValidationException {
        return decode(data, Limits.MAX_TEXTURE_DIMENSION, Limits.MAX_TEXTURE_BYTES);
    }

    public static DecodedImage decode(byte[] data, int maxDimension, int maxFileBytes) throws ValidationException {
        if (data == null || data.length < SIGNATURE.length + 12 + 13) throw new ValidationException("PNG too small");
        if (data.length > maxFileBytes) throw new ValidationException("PNG file too large");
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (data[i] != SIGNATURE[i]) throw new ValidationException("not a PNG (bad magic bytes)");
        }

        int pos = SIGNATURE.length;
        int width = -1;
        int height = -1;
        int chunkCount = 0;
        boolean seenIdat = false;
        boolean idatFinished = false;
        boolean seenIend = false;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();

        while (pos < data.length) {
            if (++chunkCount > Limits.MAX_PNG_CHUNKS) throw new ValidationException("too many PNG chunks");
            if (data.length - pos < 12) throw new ValidationException("truncated PNG chunk header");

            long length = readUInt(data, pos);
            if (length > data.length - pos - 12L) throw new ValidationException("PNG chunk length out of bounds");
            int len = (int) length;
            int typeStart = pos + 4;
            int dataStart = pos + 8;
            String type = chunkType(data, typeStart);

            CRC32 crc = new CRC32();
            crc.update(data, typeStart, 4 + len);
            if (crc.getValue() != readUInt(data, dataStart + len)) throw new ValidationException("PNG CRC mismatch in " + type);

            boolean first = chunkCount == 1;
            if (first && !type.equals("IHDR")) throw new ValidationException("PNG must start with IHDR");

            switch (type) {
                case "IHDR": {
                    if (!first || len != 13) throw new ValidationException("invalid IHDR");
                    long w = readUInt(data, dataStart);
                    long h = readUInt(data, dataStart + 4);
                    if (w < 1 || h < 1 || w > maxDimension || h > maxDimension) {
                        throw new ValidationException("PNG dimensions " + w + "x" + h + " not allowed");
                    }
                    int bitDepth = data[dataStart + 8] & 0xFF;
                    int colorType = data[dataStart + 9] & 0xFF;
                    int compression = data[dataStart + 10] & 0xFF;
                    int filter = data[dataStart + 11] & 0xFF;
                    int interlace = data[dataStart + 12] & 0xFF;
                    if (bitDepth != 8 || colorType != COLOR_TYPE_RGBA) {
                        throw new ValidationException("only 8-bit RGBA PNGs are accepted");
                    }
                    if (compression != 0 || filter != 0 || interlace != 0) {
                        throw new ValidationException("unsupported PNG compression/filter/interlace");
                    }
                    width = (int) w;
                    height = (int) h;
                    break;
                }
                case "IDAT":
                    if (idatFinished) throw new ValidationException("IDAT chunks must be consecutive");
                    seenIdat = true;
                    idat.write(data, dataStart, len);
                    break;
                case "IEND":
                    if (len != 0) throw new ValidationException("invalid IEND");
                    seenIend = true;
                    break;
                default:
                    // Uppercase first letter = critical chunk we don't understand (e.g. PLTE) -> reject.
                    if (Character.isUpperCase(type.charAt(0))) throw new ValidationException("unsupported PNG chunk " + type);
                    // Ancillary chunks (tEXt, gAMA, ...) are skipped without reading their content.
                    break;
            }
            if (seenIdat && !type.equals("IDAT")) idatFinished = true;

            pos = dataStart + len + 4;
            if (seenIend) break;
        }

        if (!seenIend) throw new ValidationException("PNG has no IEND");
        if (pos != data.length) throw new ValidationException("data after PNG IEND");
        if (!seenIdat) throw new ValidationException("PNG has no image data");

        int stride = width * BYTES_PER_PIXEL;
        byte[] raw = inflateExactly(idat.toByteArray(), height * (stride + 1));
        return new DecodedImage(width, height, unfilter(raw, width, height, stride));
    }

    /** Decompresses to exactly {@code expected} bytes; any shortfall or extra output is rejected. */
    private static byte[] inflateExactly(byte[] compressed, int expected) throws ValidationException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] out = new byte[expected];
            int offset = 0;
            int stalls = 0;
            while (offset < expected) {
                int n = inflater.inflate(out, offset, expected - offset);
                if (n == 0) {
                    if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary() || ++stalls > 16) {
                        throw new ValidationException("PNG image data is truncated");
                    }
                }
                offset += n;
            }
            if (!inflater.finished()) {
                // Try to read one more byte: if there is any, the stream is bigger than declared (bomb).
                int extra = inflater.inflate(new byte[1]);
                if (extra > 0 || !inflater.finished()) throw new ValidationException("PNG image data larger than declared");
            }
            if (inflater.getRemaining() > 0) throw new ValidationException("garbage after PNG image data");
            return out;
        } catch (DataFormatException e) {
            throw new ValidationException("PNG image data is corrupt");
        } finally {
            inflater.end();
        }
    }

    private static int[] unfilter(byte[] raw, int width, int height, int stride) throws ValidationException {
        int[] argb = new int[width * height];
        byte[] previous = new byte[stride];
        byte[] current = new byte[stride];
        int in = 0;
        for (int y = 0; y < height; y++) {
            int filterType = raw[in++] & 0xFF;
            System.arraycopy(raw, in, current, 0, stride);
            in += stride;
            for (int i = 0; i < stride; i++) {
                int a = i >= BYTES_PER_PIXEL ? current[i - BYTES_PER_PIXEL] & 0xFF : 0; // left
                int b = previous[i] & 0xFF;                                              // up
                int c = i >= BYTES_PER_PIXEL ? previous[i - BYTES_PER_PIXEL] & 0xFF : 0; // up-left
                int x = current[i] & 0xFF;
                int value;
                switch (filterType) {
                    case 0: value = x; break;
                    case 1: value = x + a; break;
                    case 2: value = x + b; break;
                    case 3: value = x + ((a + b) >>> 1); break;
                    case 4: value = x + paeth(a, b, c); break;
                    default: throw new ValidationException("invalid PNG filter type " + filterType);
                }
                current[i] = (byte) value;
            }
            for (int px = 0; px < width; px++) {
                int o = px * BYTES_PER_PIXEL;
                int r = current[o] & 0xFF;
                int g = current[o + 1] & 0xFF;
                int bl = current[o + 2] & 0xFF;
                int al = current[o + 3] & 0xFF;
                argb[y * width + px] = (al << 24) | (r << 16) | (g << 8) | bl;
            }
            byte[] swap = previous;
            previous = current;
            current = swap;
        }
        return argb;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        return pb <= pc ? b : c;
    }

    private static long readUInt(byte[] d, int i) {
        return ((long) (d[i] & 0xFF) << 24) | ((d[i + 1] & 0xFF) << 16) | ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
    }

    private static String chunkType(byte[] d, int i) throws ValidationException {
        char[] chars = new char[4];
        for (int k = 0; k < 4; k++) {
            int b = d[i + k] & 0xFF;
            if (!((b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z'))) throw new ValidationException("invalid PNG chunk type");
            chars[k] = (char) b;
        }
        return new String(chars);
    }
}
