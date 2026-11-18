package com.heytozzz.htzcosmetics.core.model;

import com.heytozzz.htzcosmetics.core.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnimationParserTest {
    private static final String CUBE = "{\"origin\":[-4,-16,-4],\"size\":[8,1,8],\"uv\":[0,0]}";

    private static CosmeticModel parse(String animations) throws ValidationException {
        String json = "{\"format\":1,\"texture\":{\"width\":64,\"height\":64},"
            + "\"bones\":[{\"name\":\"halo\",\"cubes\":[" + CUBE + "]}],\"animations\":[" + animations + "]}";
        return ModelParser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String clip(String trigger, String channels) {
        return "{\"trigger\":\"" + trigger + "\",\"length\":4,\"bones\":{\"halo\":{" + channels + "}}}";
    }

    @Test
    void parsesAndSamplesKeyframes() throws Exception {
        CosmeticModel m = parse(clip("idle",
            "\"rotation\":{\"keyframes\":[[0,0,0,0],[4,0,360,0]]},"
                + "\"position\":{\"interpolation\":\"step\",\"keyframes\":[[0,0,0,0],[2,0,-1,0]]},"
                + "\"scale\":{\"interpolation\":\"smooth\",\"keyframes\":[[0,1,1,1],[2,2,2,2],[4,1,1,1]]}"));
        AnimationClip c = m.animations().get(0);
        assertEquals(AnimationClip.Trigger.IDLE, c.trigger());
        AnimationClip.BoneTrack t = c.tracks().get(0);
        float[] out = new float[3];
        t.rotation().sample(1, out);
        assertArrayEquals(new float[]{0, 90, 0}, out, 1e-4f);         // linear
        t.position().sample(1.99f, out);
        assertArrayEquals(new float[]{0, 0, 0}, out, 1e-4f);          // step holds the previous keyframe
        t.position().sample(3, out);
        assertArrayEquals(new float[]{0, -1, 0}, out, 1e-4f);         // after the last keyframe: hold
        t.scale().sample(2, out);
        assertArrayEquals(new float[]{2, 2, 2}, out, 1e-4f);          // smooth passes through keyframes
        t.scale().sample(1, out);
        assertEquals(1.5f + 0.125f, out[0], 0.2f);                    // smooth curve between them
    }

    @Test
    void modelsWithoutAnimationsStillWorkAndUnknownTriggersAreIgnored() throws Exception {
        String json = "{\"format\":1,\"texture\":{\"width\":64,\"height\":64},\"particles\":{},"
            + "\"bones\":[{\"name\":\"halo\",\"cubes\":[" + CUBE + "]}]}";
        assertEquals(0, ModelParser.parse(json.getBytes(StandardCharsets.UTF_8)).animations().size());
        assertEquals(0, parse(clip("dance", "\"rotation\":{\"keyframes\":[[0,0,0,0]]}")).animations().size());
        assertNull(parse(clip("walk", "\"unknownChannel\":{}")).animations().get(0).tracks().stream().findFirst().orElse(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // Expressions are not numbers: Molang/JS/anything is rejected.
        "\"rotation\":{\"keyframes\":[[0,\"math.sin(query.anim_time*90)\",0,0]]}",
        "\"rotation\":{\"keyframes\":[[0,\"10\",0,0]]}",
        "\"rotation\":{\"keyframes\":[[0,1e400,0,0]]}",                      // infinity
        "\"rotation\":{\"keyframes\":[[0,99999,0,0]]}",                      // out of range
        "\"position\":{\"keyframes\":[[0,0,500,0]]}",
        "\"scale\":{\"keyframes\":[[0,-1,1,1]]}",                            // negative scale
        "\"scale\":{\"keyframes\":[[0,100,1,1]]}",
        "\"rotation\":{\"keyframes\":[[2,0,0,0],[1,0,0,0]]}",                // times going backwards
        "\"rotation\":{\"keyframes\":[[1,0,0,0],[1,0,0,0]]}",                // duplicate time
        "\"rotation\":{\"keyframes\":[[5,0,0,0]]}",                          // after the clip length
        "\"rotation\":{\"keyframes\":[[-1,0,0,0]]}",
        "\"rotation\":{\"keyframes\":[[0,0,0]]}",                            // wrong size
        "\"rotation\":{\"keyframes\":[]}",
        "\"rotation\":{\"interpolation\":\"bezier\",\"keyframes\":[[0,0,0,0]]}",
        "\"rotation\":{\"keyframes\":\"[[0,0,0,0]]\"}"
    })
    void rejectsBadChannels(String channels) {
        assertThrows(ValidationException.class, () -> parse(clip("idle", channels)));
    }

    @Test
    void rejectsStructuralAbuse() {
        String ok = "\"rotation\":{\"keyframes\":[[0,0,0,0]]}";
        assertThrows(ValidationException.class, () -> parse(clip("idle", ok) + "," + clip("idle", ok)));      // duplicate trigger
        assertThrows(ValidationException.class,                                                               // unknown bone
            () -> parse("{\"trigger\":\"idle\",\"length\":1,\"bones\":{\"../evil\":{" + ok + "}}}"));
        assertThrows(ValidationException.class,
            () -> parse("{\"trigger\":\"idle\",\"length\":0,\"bones\":{}}"));                               // zero length
        assertThrows(ValidationException.class,
            () -> parse("{\"trigger\":\"idle\",\"length\":100000,\"bones\":{}}"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 65; i++) many.append(i == 0 ? "" : ",").append("[").append(i * 0.01).append(",0,0,0]");
        assertThrows(ValidationException.class,                                                               // > 64 keyframes
            () -> parse(clip("idle", "\"rotation\":{\"keyframes\":[" + many + "]}")));
        StringBuilder clips = new StringBuilder();
        for (int i = 0; i < 9; i++) clips.append(i == 0 ? "" : ",").append(clip("future" + i, ok));
        assertThrows(ValidationException.class, () -> parse(clips.toString()));                              // > 8 clips
    }
}
