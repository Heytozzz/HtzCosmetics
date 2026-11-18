package com.heytozzz.htzcosmetics.core.model;

import com.heytozzz.htzcosmetics.core.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelParserTest {
    private static final String CUBE = "{\"origin\":[-4,-16,-4],\"size\":[8,8,8],\"uv\":[0,0],\"inflate\":0.5}";

    private static String model(String bones) {
        return "{\"format\":1,\"texture\":{\"width\":64,\"height\":64},\"bones\":[" + bones + "]}";
    }

    private static CosmeticModel parse(String json) throws ValidationException {
        return ModelParser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesValidModel() throws Exception {
        CosmeticModel m = parse(model(
            "{\"name\":\"hat\",\"parent\":null,\"pivot\":[0,-8,0],\"cubes\":[" + CUBE + "]},"
                + "{\"name\":\"brim\",\"parent\":\"hat\",\"rotation\":[0,45,0],"
                + "\"cubes\":[{\"origin\":[-6,-9,-6],\"size\":[12,1,12],\"uv\":[0,16],\"mirror\":true}]}"));
        assertEquals(2, m.bones().size());
        assertEquals(2, m.cubeCount());
        assertEquals(0, m.bones().get(1).parentIndex());
        assertEquals(45.0, m.bones().get(1).rotation()[1]);
        assertEquals(0.5, m.bones().get(0).cubes().get(0).inflate());
    }

    @Test
    void ignoresUnknownKeysLikeFutureAnimations() throws Exception {
        String json = "{\"format\":1,\"particles\":{\"idle\":{}},\"texture\":{\"width\":64,\"height\":64},"
            + "\"bones\":[{\"name\":\"a\",\"cubes\":[" + CUBE + "]}]}";
        assertEquals(1, parse(json).cubeCount());
    }

    @Test
    void rejectsTooManyCubes() {
        String cubes = String.join(",", java.util.Collections.nCopies(300,
            "{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}"));
        assertThrows(ValidationException.class, () -> parse(model("{\"name\":\"a\",\"cubes\":[" + cubes + "]}")));
    }

    @Test
    void rejectsTooManyCubesSpreadAcrossBones() {
        StringBuilder bones = new StringBuilder();
        String cubes = String.join(",", java.util.Collections.nCopies(200,
            "{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}"));
        for (int i = 0; i < 2; i++) {
            if (i > 0) bones.append(',');
            bones.append("{\"name\":\"b").append(i).append("\",\"cubes\":[").append(cubes).append("]}");
        }
        assertThrows(ValidationException.class, () -> parse(model(bones.toString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[1e6,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}",      // far away
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[1e400,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}",    // infinity
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[-1,1,1],\"uv\":[0,0]}]}",       // negative size
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[30,0,0],\"size\":[8,1,1],\"uv\":[0,0]}]}",       // exceeds volume
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0.5,0]}]}",      // fractional uv
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[30,30,30],\"uv\":[0,0]}]}",     // uv outside texture
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0],\"inflate\":100}]}",
        "{\"name\":\"a\",\"cubes\":[{\"origin\":[0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}",          // wrong vector size
        "{\"name\":\"a\",\"cubes\":[{\"origin\":\"0,0,0\",\"size\":[1,1,1],\"uv\":[0,0]}]}",      // wrong type
        "{\"name\":\"a\",\"rotation\":[0,99999,0],\"cubes\":[" + CUBE + "]}",                       // huge rotation
        "{\"name\":\"../x\",\"cubes\":[" + CUBE + "]}",                                             // bad bone name
        "{\"name\":\"a\",\"parent\":\"a\",\"cubes\":[" + CUBE + "]}",                               // self parent
        "{\"name\":\"a\",\"parent\":\"b\",\"cubes\":[" + CUBE + "]},{\"name\":\"b\",\"cubes\":[]}",  // forward ref
        "{\"name\":\"a\",\"cubes\":[" + CUBE + "]},{\"name\":\"a\",\"cubes\":[]}",                  // duplicate name
        "{\"name\":\"a\",\"cubes\":[]}"                                                              // no cubes at all
    })
    void rejectsMaliciousOrBrokenBones(String bones) {
        assertThrows(ValidationException.class, () -> parse(model(bones)));
    }

    @Test
    void rejectsWrongFormatAndTexture() {
        String valid = model("{\"name\":\"a\",\"cubes\":[" + CUBE + "]}");
        assertThrows(ValidationException.class, () -> parse(valid.replace("\"format\":1", "\"format\":2")));
        assertThrows(ValidationException.class,
            () -> parse(model("{\"name\":\"a\",\"cubes\":[" + CUBE + "]}").replace("\"width\":64", "\"width\":100000")));
        assertThrows(ValidationException.class, () -> parse(model("")));
    }

    @Test
    void rejectsNonJsonPayloads() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] classFile = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, 65};
        byte[] zip = {'P', 'K', 3, 4, 0, 0};
        assertThrows(ValidationException.class, () -> ModelParser.parse(png));
        assertThrows(ValidationException.class, () -> ModelParser.parse(classFile));
        assertThrows(ValidationException.class, () -> ModelParser.parse(zip));
        assertThrows(ValidationException.class, () -> ModelParser.parse(new byte[200 * 1024]));
    }
}
