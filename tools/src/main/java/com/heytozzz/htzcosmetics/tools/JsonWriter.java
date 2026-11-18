package com.heytozzz.htzcosmetics.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Deterministic JSON writer for the files we publish (manifest and models).
 * Output is pure ASCII: non-ASCII characters are written as {@code \\uXXXX} escapes.
 * Numbers are rounded to 4 decimals to remove floating-point noise like 0.30000000000000004.
 */
public final class JsonWriter {
    private JsonWriter() {
    }

    public static byte[] toBytes(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static void write(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Integer || value instanceof Long) {
            sb.append(((Number) value).longValue());
        } else if (value instanceof Number n) {
            sb.append(formatDouble(n.doubleValue()));
        } else if (value instanceof double[] arr) {
            sb.append('[');
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatDouble(arr[i]));
            }
            sb.append(']');
        } else if (value instanceof Map<?, ?> map) {
            writeMap(sb, map, indent);
        } else if (value instanceof List<?> list) {
            writeList(sb, list, indent);
        } else {
            throw new IllegalArgumentException("Cannot write " + value.getClass().getSimpleName() + " as JSON");
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            pad(sb, indent + 1);
            writeString(sb, (String) e.getKey());
            sb.append(": ");
            write(sb, e.getValue(), indent + 1);
            if (++i < map.size()) sb.append(',');
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        if (list.stream().allMatch(v -> v instanceof Number || v instanceof Boolean)) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                write(sb, list.get(i), indent);
            }
            sb.append(']');
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            pad(sb, indent + 1);
            write(sb, list.get(i), indent + 1);
            if (i + 1 < list.size()) sb.append(',');
            sb.append('\n');
        }
        pad(sb, indent);
        sb.append(']');
    }

    static String formatDouble(double d) {
        if (!Double.isFinite(d)) throw new IllegalArgumentException("non-finite number");
        BigDecimal bd = BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        if (bd.signum() == 0) return "0";
        return bd.toPlainString();
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }
}
