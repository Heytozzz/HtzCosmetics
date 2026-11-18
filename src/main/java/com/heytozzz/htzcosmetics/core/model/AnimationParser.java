package com.heytozzz.htzcosmetics.core.model;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the optional {@code "animations"} block of a model. Only numbers: there is no place for expressions,
 * scripts or formulas in this format (a string where a number is expected is rejected).
 *
 * <pre>{@code
 * "animations": [
 *   { "trigger": "idle", "length": 4,
 *     "bones": {
 *       "halo": {
 *         "rotation": { "interpolation": "linear", "keyframes": [[0, 0,0,0], [4, 0,360,0]] },
 *         "position": { "interpolation": "smooth", "keyframes": [[0, 0,0,0], [2, 0,-1,0], [4, 0,0,0]] }
 *       } } }
 * ]
 * }</pre>
 * Keyframe = {@code [time, x, y, z]}. Unknown triggers and channels are ignored (for future versions);
 * everything else that is wrong rejects the model.
 */
final class AnimationParser {
    private AnimationParser() {
    }

    static List<AnimationClip> parse(Map<String, Object> root, List<Bone> bones) throws ValidationException {
        if (!Json.has(root, "animations")) return List.of();
        List<Object> list = Json.array(root, "animations", Limits.MAX_ANIMATIONS);
        List<AnimationClip> clips = new ArrayList<>();
        EnumSet<AnimationClip.Trigger> seen = EnumSet.noneOf(AnimationClip.Trigger.class);
        int[] totalKeyframes = {0};

        for (Object entry : list) {
            Map<String, Object> clip = Json.asObject(entry, "animation");
            AnimationClip.Trigger trigger = trigger(Json.string(clip, "trigger", 32));
            if (trigger == null) continue; // a trigger from a newer format version: ignored
            if (!seen.add(trigger)) throw new ValidationException("duplicate animation trigger " + trigger);
            float length = (float) Json.number(clip, "length", Limits.MIN_ANIMATION_LENGTH, Limits.MAX_ANIMATION_LENGTH);

            List<AnimationClip.BoneTrack> tracks = new ArrayList<>();
            for (Map.Entry<String, Object> e : Json.object(clip, "bones").entrySet()) {
                int boneIndex = indexOf(bones, e.getKey());
                if (boneIndex < 0) throw new ValidationException("animation refers to unknown bone");
                Map<String, Object> channels = Json.asObject(e.getValue(), "bone animation");
                Channel rotation = channel(channels, "rotation", length, Limits.MAX_ANIMATION_ROTATION, -Limits.MAX_ANIMATION_ROTATION, totalKeyframes);
                Channel position = channel(channels, "position", length, Limits.MAX_ANIMATION_OFFSET, -Limits.MAX_ANIMATION_OFFSET, totalKeyframes);
                Channel scale = channel(channels, "scale", length, Limits.MAX_ANIMATION_SCALE, 0, totalKeyframes);
                if (rotation != null || position != null || scale != null) {
                    tracks.add(new AnimationClip.BoneTrack(boneIndex, rotation, position, scale));
                }
            }
            clips.add(new AnimationClip(trigger, length, Collections.unmodifiableList(tracks)));
        }
        return Collections.unmodifiableList(clips);
    }

    private static AnimationClip.Trigger trigger(String name) {
        for (AnimationClip.Trigger t : AnimationClip.Trigger.values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(name)) return t;
        }
        return null;
    }

    private static int indexOf(List<Bone> bones, String name) {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).name().equals(name)) return i;
        }
        return -1;
    }

    private static Channel channel(Map<String, Object> channels, String key, float length, double max, double min,
                                   int[] totalKeyframes) throws ValidationException {
        if (!Json.has(channels, key)) return null;
        Map<String, Object> c = Json.object(channels, key);
        Channel.Interpolation interpolation = Channel.Interpolation.LINEAR;
        if (Json.has(c, "interpolation")) {
            String name = Json.string(c, "interpolation", 16);
            interpolation = switch (name) {
                case "linear" -> Channel.Interpolation.LINEAR;
                case "step" -> Channel.Interpolation.STEP;
                case "smooth" -> Channel.Interpolation.SMOOTH;
                default -> throw new ValidationException("unknown interpolation");
            };
        }
        List<Object> frames = Json.array(c, "keyframes", Limits.MAX_KEYFRAMES_PER_CHANNEL);
        if (frames.isEmpty()) throw new ValidationException("channel without keyframes");
        totalKeyframes[0] += frames.size();
        if (totalKeyframes[0] > Limits.MAX_TOTAL_KEYFRAMES) throw new ValidationException("too many keyframes");

        float[] times = new float[frames.size()];
        float[] values = new float[frames.size() * 3];
        for (int i = 0; i < frames.size(); i++) {
            List<Object> f = Json.asArray(frames.get(i), "keyframe");
            if (f.size() != 4) throw new ValidationException("keyframe must be [time, x, y, z]");
            times[i] = (float) Json.asNumber(f.get(0), "time", 0, length);
            if (i > 0 && times[i] <= times[i - 1]) throw new ValidationException("keyframe times must increase");
            for (int axis = 0; axis < 3; axis++) values[i * 3 + axis] = (float) Json.asNumber(f.get(axis + 1), key, min, max);
        }
        return new Channel(interpolation, times, values);
    }
}
