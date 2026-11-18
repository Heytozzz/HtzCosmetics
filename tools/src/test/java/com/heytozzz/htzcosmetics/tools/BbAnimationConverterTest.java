package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.model.AnimationClip;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BbAnimationConverterTest {
    private static String keyframe(String channel, double time, String x, String y, String z, String interp) {
        return "{\"channel\":\"" + channel + "\",\"time\":" + time + ",\"interpolation\":\"" + interp + "\","
            + "\"data_points\":[{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}]}";
    }

    private static String model(String animations) {
        return BbmodelConverterTest.bbmodel(BbmodelConverterTest.cube("c1", "-4,36,-4", "4,37,4", ""),
            "{\"name\":\"halo\",\"origin\":[0,36,0],\"uuid\":\"g1\",\"children\":[\"c1\"]}",
            ",\"animations\":[" + animations + "]");
    }

    private static BbmodelConverter.Result convert(String animations) {
        return BbmodelConverter.convert(model(animations).getBytes(StandardCharsets.UTF_8), Anchor.HEAD, "t");
    }

    @Test
    void convertsIdleAndWalkWithModelCoordinates() throws Exception {
        String idle = "{\"name\":\"animation.halo.idle\",\"loop\":\"loop\",\"length\":4,\"animators\":{\"g1\":{\"name\":\"halo\","
            + "\"type\":\"bone\",\"keyframes\":["
            + keyframe("rotation", 4, "0", "\"360\"", "0", "linear") + ","
            + keyframe("rotation", 0, "0", "0", "0", "linear") + ","
            + keyframe("position", 0, "0", "0", "0", "catmullrom") + ","
            + keyframe("position", 2, "0", "1", "0", "catmullrom") + ","
            + keyframe("position", 4, "0", "0", "0", "catmullrom") + "]},"
            + "\"effects\":{\"type\":\"effect\",\"keyframes\":[]}}}";
        String walk = "{\"name\":\"walk\",\"length\":1,\"animators\":{\"g1\":{\"type\":\"bone\",\"keyframes\":["
            + keyframe("scale", 0, "1", "1", "1", "step") + "," + keyframe("scale", 0.5, "2", "2", "2", "step") + "]}}}";
        String other = "{\"name\":\"dance\",\"length\":1,\"animators\":{}}";

        BbmodelConverter.Result r = convert(idle + "," + walk + "," + other);
        CosmeticModel m = ModelParser.parse(r.modelJson());
        assertEquals(2, m.animations().size());
        AnimationClip idleClip = m.animations().get(0);
        assertEquals(AnimationClip.Trigger.IDLE, idleClip.trigger());
        assertEquals(4f, idleClip.length());

        float[] out = new float[3];
        idleClip.tracks().get(0).rotation().sample(2, out);
        assertArrayEquals(new float[]{0, -180, 0}, out, 1e-3f);    // y flipped like the static model
        idleClip.tracks().get(0).position().sample(2, out);
        assertArrayEquals(new float[]{0, -1, 0}, out, 1e-3f);      // up in Blockbench = negative Y in Minecraft

        assertEquals(AnimationClip.Trigger.WALK, m.animations().get(1).trigger());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("dance")));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("effect")));
    }

    @Test
    void rejectsMolangEverywhere() {
        for (String expr : new String[]{"\"math.sin(query.anim_time*90)*10\"", "\"q.life_time\"", "\"1+1\"",
            "\"v.x = 5; return v.x;\"", "\"NaN\"", "\"1e999\""}) {
            String anim = "{\"name\":\"idle\",\"length\":1,\"animators\":{\"g1\":{\"type\":\"bone\",\"keyframes\":["
                + keyframe("rotation", 0, "0", expr, "0", "linear") + "]}}}";
            ToolException e = assertThrows(ToolException.class, () -> convert(anim), expr);
            assertTrue(e.getMessage().contains("Molang"), e.getMessage());
        }
    }

    @Test
    void modelsWithoutAnimationsHaveNoAnimationsBlock() {
        BbmodelConverter.Result r = convert("");
        assertFalse(new String(r.modelJson(), StandardCharsets.US_ASCII).contains("animations"));
    }

    @Test
    void rejectsDuplicateTriggersAndTooLong() {
        String a = "{\"name\":\"idle\",\"length\":1,\"animators\":{\"g1\":{\"type\":\"bone\",\"keyframes\":["
            + keyframe("rotation", 0, "0", "0", "0", "linear") + "]}}}";
        assertThrows(ToolException.class, () -> convert(a + "," + a));
        String longAnim = a.replace("\"length\":1", "\"length\":500");
        assertThrows(ToolException.class, () -> convert(longAnim));
    }
}
