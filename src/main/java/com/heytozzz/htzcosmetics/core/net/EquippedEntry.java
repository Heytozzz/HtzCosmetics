package com.heytozzz.htzcosmetics.core.net;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One worn cosmetic as stored and sent: {@code "top_hat"} or, with a chosen colour, {@code "top_hat:3070ff"}.
 * Strictly validated: an id of a-z, 0-9 and _, and optionally exactly six lowercase hex digits.
 *
 * @param color 0xRRGGBB, or null when the player kept the default colour
 */
public record EquippedEntry(String id, Integer color) {
    public static final Pattern FORMAT = Pattern.compile("([a-z0-9_]{1,32})(?::([0-9a-f]{6}))?");
    /** Longest valid entry: 32-character id + ":" + 6 hex digits. */
    public static final int MAX_LENGTH = 39;

    /** @return the entry, or null if {@code text} is not valid */
    public static EquippedEntry parse(String text) {
        if (text == null || text.length() > MAX_LENGTH) return null;
        Matcher m = FORMAT.matcher(text);
        if (!m.matches()) return null;
        return new EquippedEntry(m.group(1), m.group(2) == null ? null : Integer.parseInt(m.group(2), 16));
    }

    public String format() {
        return color == null ? id : id + ":" + String.format(Locale.ROOT, "%06x", color & 0xFFFFFF);
    }
}
