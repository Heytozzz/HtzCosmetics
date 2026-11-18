package com.heytozzz.htzcosmetics.core.json;

import com.heytozzz.htzcosmetics.core.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictJsonParserTest {
    private static Object parse(String json) throws ValidationException {
        return StrictJsonParser.parse(json.getBytes(StandardCharsets.UTF_8), 1 << 20);
    }

    @Test
    void parsesPlainData() throws Exception {
        Map<String, Object> obj = Json.asObject(
            parse(" {\"a\": [1, -2.5, 3e2], \"b\": \"x\\u00e9\\n\", \"c\": true, \"d\": null} "), "root");
        assertEquals(List.of(1.0, -2.5, 300.0), obj.get("a"));
        assertEquals("x\u00e9\n", obj.get("b"));
        assertEquals(Boolean.TRUE, obj.get("c"));
        assertSame(StrictJsonParser.NULL, obj.get("d"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",                       // empty
        "{",                      // unterminated
        "{\"a\":1,}",             // trailing comma
        "{\"a\":1}{}",            // trailing data
        "{\"a\":1,\"a\":2}",      // duplicate key (ambiguous meaning)
        "[1e400]",                // infinite number
        "[-1e400]",
        "[NaN]",                  // not JSON
        "[Infinity]",
        "[01]",                   // leading zero
        "[1.]",
        "[.5]",
        "[+1]",
        "['single']",
        "{a:1}",                  // unquoted key
        "[\"tab\tinside\"]",      // raw control char
        "[\"bad \\x escape\"]",
        "[\"\\u12\"]",
        "[tru]",
        "/* comment */ {}",
        "[1] // comment",
        "[11111111111111111111111111111111111111]"  // number token too long
    })
    void rejectsInvalidOrDangerousJson(String json) {
        assertThrows(ValidationException.class, () -> parse(json));
    }

    @Test
    void rejectsDeepNestingWithoutStackOverflow() {
        String deep = "[".repeat(100_000) + "]".repeat(100_000);
        assertThrows(ValidationException.class, () -> parse(deep));
    }

    @Test
    void rejectsLongStrings() {
        String json = "[\"" + "a".repeat(10_000) + "\"]";
        assertThrows(ValidationException.class, () -> parse(json));
    }

    @Test
    void rejectsTooManyEntries() {
        String json = "[" + "0,".repeat(10_000) + "0]";
        assertThrows(ValidationException.class, () -> parse(json));
    }

    @Test
    void rejectsOversizedInput() {
        byte[] big = new byte[2048];
        assertThrows(ValidationException.class, () -> StrictJsonParser.parse(big, 1024));
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] bad = {'[', '"', (byte) 0xC3, (byte) 0x28, '"', ']'};
        assertThrows(ValidationException.class, () -> StrictJsonParser.parse(bad, 1024));
    }

    @Test
    void rejectsPngBytesAsJson() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
        assertThrows(ValidationException.class, () -> StrictJsonParser.parse(png, 1024));
    }

    @Test
    void typedAccessorsCheckRanges() throws Exception {
        Map<String, Object> obj = Json.asObject(parse("{\"n\": 1.5, \"big\": 1e300, \"s\": 5}"), "root");
        assertThrows(ValidationException.class, () -> Json.integer(obj, "n", 0, 10));
        assertThrows(ValidationException.class, () -> Json.number(obj, "big", -100, 100));
        assertThrows(ValidationException.class, () -> Json.string(obj, "s", 10));
        assertThrows(ValidationException.class, () -> Json.string(obj, "missing", 10));
    }
}
