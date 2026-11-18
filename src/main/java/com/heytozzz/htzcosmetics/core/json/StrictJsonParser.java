package com.heytozzz.htzcosmetics.core.json;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, strict JSON parser written for untrusted input.
 *
 * <p>It only produces plain data: {@code Map<String,Object>}, {@code List<Object>}, {@code String},
 * {@code Double}, {@code Boolean} and {@link #NULL}. It never creates objects of other classes,
 * so there is no polymorphic deserialization. Size, depth, string length, entry count, duplicate keys,
 * invalid UTF-8 and non-finite numbers are all rejected.
 */
public final class StrictJsonParser {
    /** Marker for JSON {@code null}. */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private static final int MAX_NUMBER_TOKEN_LENGTH = 32;

    private final String text;
    private final int maxDepth;
    private final int maxStringLength;
    private final int maxContainerEntries;
    private final int maxTotalValues;
    private int pos;
    private int totalValues;

    private StrictJsonParser(String text, int maxDepth, int maxStringLength, int maxContainerEntries, int maxTotalValues) {
        this.text = text;
        this.maxDepth = maxDepth;
        this.maxStringLength = maxStringLength;
        this.maxContainerEntries = maxContainerEntries;
        this.maxTotalValues = maxTotalValues;
    }

    /** Parses with the default limits from {@link Limits}. */
    public static Object parse(byte[] data, int maxBytes) throws ValidationException {
        return parse(data, maxBytes, Limits.MAX_JSON_DEPTH, Limits.MAX_JSON_STRING_LENGTH,
            Limits.MAX_JSON_CONTAINER_ENTRIES, Limits.MAX_JSON_TOTAL_VALUES);
    }

    public static Object parse(byte[] data, int maxBytes, int maxDepth, int maxStringLength,
                               int maxContainerEntries, int maxTotalValues) throws ValidationException {
        if (data == null || data.length == 0) throw new ValidationException("JSON is empty");
        if (data.length > maxBytes) throw new ValidationException("JSON too large: " + data.length + " bytes");

        String text = decodeUtf8Strict(data);
        StrictJsonParser parser = new StrictJsonParser(text, maxDepth, maxStringLength, maxContainerEntries, maxTotalValues);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.pos != text.length()) throw parser.error("unexpected data after JSON value");
        return value;
    }

    private static String decodeUtf8Strict(byte[] data) throws ValidationException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
                .toString();
        } catch (CharacterCodingException e) {
            throw new ValidationException("JSON is not valid UTF-8");
        }
    }

    private Object readValue(int depth) throws ValidationException {
        if (depth > maxDepth) throw error("nesting too deep");
        if (++totalValues > maxTotalValues) throw error("too many values");
        if (pos >= text.length()) throw error("unexpected end of input");

        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readJsonObject(depth);
            case '[':
                return readJsonArray(depth);
            case '"':
                return readString();
            case 't':
                return readLiteral("true", Boolean.TRUE);
            case 'f':
                return readLiteral("false", Boolean.FALSE);
            case 'n':
                return readLiteral("null", NULL);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                throw error("unexpected character");
        }
    }

    private Map<String, Object> readJsonObject(int depth) throws ValidationException {
        pos++; // '{'
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected object key");
            String key = readString();
            if (map.containsKey(key)) throw error("duplicate key '" + key + "'");
            skipWhitespace();
            if (next() != ':') throw error("expected ':'");
            skipWhitespace();
            map.put(key, readValue(depth + 1));
            if (map.size() > maxContainerEntries) throw error("object has too many entries");
            skipWhitespace();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw error("expected ',' or '}'");
        }
    }

    private List<Object> readJsonArray(int depth) throws ValidationException {
        pos++; // '['
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue(depth + 1));
            if (list.size() > maxContainerEntries) throw error("array has too many entries");
            skipWhitespace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw error("expected ',' or ']'");
        }
    }

    private String readString() throws ValidationException {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) throw error("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c < 0x20) throw error("control character in string");
            if (c == '\\') {
                if (pos >= text.length()) throw error("unterminated escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': sb.append(readUnicodeEscape()); break;
                    default: throw error("invalid escape");
                }
            } else {
                sb.append(c);
            }
            if (sb.length() > maxStringLength) throw error("string too long");
        }
    }

    private char readUnicodeEscape() throws ValidationException {
        if (pos + 4 > text.length()) throw error("truncated unicode escape");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos++), 16);
            if (digit < 0) throw error("invalid unicode escape");
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Double readNumber() throws ValidationException {
        int start = pos;
        if (peek() == '-') pos++;
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) pos++;
        } else {
            throw error("invalid number");
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) throw error("invalid number");
            while (isDigit(peek())) pos++;
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            if (!isDigit(peek())) throw error("invalid number");
            while (isDigit(peek())) pos++;
        }
        if (pos - start > MAX_NUMBER_TOKEN_LENGTH) throw error("number too long");
        double value = Double.parseDouble(text.substring(start, pos));
        if (!Double.isFinite(value)) throw error("number is not finite");
        return value;
    }

    private Object readLiteral(String literal, Object value) throws ValidationException {
        if (!text.startsWith(literal, pos)) throw error("invalid literal");
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            pos++;
        }
    }

    /** Returns the current char, or 0 at end of input (0 never matches any expected token). */
    private char peek() {
        return pos < text.length() ? text.charAt(pos) : 0;
    }

    private char next() throws ValidationException {
        if (pos >= text.length()) throw error("unexpected end of input");
        return text.charAt(pos++);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private ValidationException error(String message) {
        return new ValidationException("Invalid JSON at offset " + pos + ": " + message);
    }
}
