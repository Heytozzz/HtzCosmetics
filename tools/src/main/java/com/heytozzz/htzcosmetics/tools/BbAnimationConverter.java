package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Converts Blockbench animations into our plain keyframe format.
 *
 * <ul>
 *   <li>Only animations named {@code idle}, {@code walk} or {@code sneak} (also {@code crouch}) are used; a prefix such
 *       as {@code animation.model.} is fine. Others are skipped with a warning.</li>
 *   <li>Every keyframe value must be a plain number. Molang expressions ({@code math.sin(...)}, {@code query.*} ...)
 *       stop the build with a clear message: they would be code, and the mod never runs code from downloads.</li>
 *   <li>Coordinates are converted the same way as the model (x and y flipped for rotation and position).</li>
 *   <li>Interpolation: linear, step, catmullrom (smooth). Bezier becomes linear (warning).</li>
 * </ul>
 */
final class BbAnimationConverter {
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\s*-?(\\d+(\\.\\d*)?|\\.\\d+)\\s*");

    private BbAnimationConverter() {
    }

    static List<Object> convert(Map<String, Object> root, Map<String, String> boneByGroupUuid, String label,
                                List<String> warnings) throws ValidationException {
        List<Object> out = new ArrayList<>();
        if (!Json.has(root, "animations")) return out;
        Map<String, Boolean> usedTriggers = new HashMap<>();

        for (Object a : Json.asArray(root.get("animations"), "animations")) {
            Map<String, Object> anim = Json.asObject(a, "animation");
            String name = Json.has(anim, "name") ? Json.asString(anim.get("name"), "name", 256) : "";
            String trigger = trigger(name);
            if (trigger == null) {
                warnings.add("animation '" + name + "' skipped: name it idle, walk or sneak to use it");
                continue;
            }
            if (usedTriggers.put(trigger, true) != null) {
                throw new ToolException(label + ": two animations are both '" + trigger + "'");
            }
            String loop = Json.has(anim, "loop") ? Json.asString(anim.get("loop"), "loop", 16) : "loop";
            if (!loop.equals("loop")) warnings.add("animation '" + name + "' is set to '" + loop + "'; cosmetics always loop");

            Map<String, Object> bones = new LinkedHashMap<>();
            double maxTime = 0;
            Map<String, Object> animators = Json.has(anim, "animators") ? Json.object(anim, "animators") : Map.of();
            for (Map.Entry<String, Object> e : animators.entrySet()) {
                Map<String, Object> animator = Json.asObject(e.getValue(), "animator");
                String type = Json.has(animator, "type") ? Json.asString(animator.get("type"), "type", 32) : "bone";
                if (!type.equals("bone")) {
                    warnings.add("animation '" + name + "': " + type + " tracks (sounds, particles...) are ignored");
                    continue;
                }
                String bone = boneByGroupUuid.get(e.getKey());
                if (bone == null) continue; // animator for a hidden/removed group
                Map<String, Object> channels = new LinkedHashMap<>();
                for (String channel : List.of("rotation", "position", "scale")) {
                    ChannelResult c = channel(animator, channel, label, name, warnings);
                    if (c == null) continue;
                    channels.put(channel, c.json());
                    maxTime = Math.max(maxTime, c.lastTime());
                }
                if (!channels.isEmpty()) bones.put(bone, channels);
            }
            if (bones.isEmpty()) {
                warnings.add("animation '" + name + "' has no keyframes on visible groups; skipped");
                continue;
            }
            double length = Json.has(anim, "length") ? Json.asNumber(anim.get("length"), "length", 0, 1e6) : 0;
            if (length < maxTime) length = maxTime;
            length = Math.max(Limits.MIN_ANIMATION_LENGTH, length);
            if (length > Limits.MAX_ANIMATION_LENGTH) {
                throw new ToolException(label + ": animation '" + name + "' is longer than "
                    + (int) Limits.MAX_ANIMATION_LENGTH + " seconds");
            }
            Map<String, Object> clip = new LinkedHashMap<>();
            clip.put("trigger", trigger);
            clip.put("length", length);
            clip.put("bones", bones);
            out.add(clip);
        }
        return out;
    }

    private record ChannelResult(Map<String, Object> json, double lastTime) {
    }

    private static ChannelResult channel(Map<String, Object> animator, String channel, String label, String anim,
                                         List<String> warnings) throws ValidationException {
        if (!Json.has(animator, "keyframes")) return null;
        TreeMap<Double, double[]> frames = new TreeMap<>();
        Map<String, Integer> interpolationVotes = new HashMap<>();
        for (Object k : Json.asArray(animator.get("keyframes"), "keyframes")) {
            Map<String, Object> kf = Json.asObject(k, "keyframe");
            if (!Json.has(kf, "channel") || !Json.asString(kf.get("channel"), "channel", 32).equals(channel)) continue;
            double time = Json.asNumber(kf.get("time"), "time", 0, 1e6);
            List<Object> points = Json.asArray(kf.get("data_points"), "data_points");
            if (points.isEmpty()) continue;
            // With two points (pre/post), Blockbench shows the second one from this keyframe on.
            Map<String, Object> p = Json.asObject(points.get(points.size() - 1), "data point");
            double x = number(p.get("x"), label, anim, channel);
            double y = number(p.get("y"), label, anim, channel);
            double z = number(p.get("z"), label, anim, channel);
            double[] v = switch (channel) {
                case "rotation", "position" -> new double[]{-x, -y, z}; // Blockbench -> Minecraft model space
                default -> new double[]{x, y, z};
            };
            frames.put(time, v);
            String interp = Json.has(kf, "interpolation") ? Json.asString(kf.get("interpolation"), "interpolation", 32) : "linear";
            interpolationVotes.merge(interp, 1, Integer::sum);
        }
        if (frames.isEmpty()) return null;
        if (frames.size() > Limits.MAX_KEYFRAMES_PER_CHANNEL) {
            throw new ToolException(label + ": animation '" + anim + "' has more than "
                + Limits.MAX_KEYFRAMES_PER_CHANNEL + " " + channel + " keyframes on one group");
        }
        String interpolation = "linear";
        int best = -1;
        for (Map.Entry<String, Integer> v : interpolationVotes.entrySet()) {
            if (v.getValue() > best) {
                best = v.getValue();
                interpolation = v.getKey();
            }
        }
        if (interpolationVotes.size() > 1) {
            warnings.add("animation '" + anim + "': mixed interpolation on one channel; using " + interpolation);
        }
        String ours = switch (interpolation) {
            case "step" -> "step";
            case "catmullrom" -> "smooth";
            case "linear" -> "linear";
            default -> {
                warnings.add("animation '" + anim + "': '" + interpolation + "' interpolation becomes linear");
                yield "linear";
            }
        };
        List<Object> keyframes = new ArrayList<>();
        for (Map.Entry<Double, double[]> f : frames.entrySet()) {
            keyframes.add(List.of(f.getKey(), f.getValue()[0], f.getValue()[1], f.getValue()[2]));
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("interpolation", ours);
        json.put("keyframes", keyframes);
        return new ChannelResult(json, frames.lastKey());
    }

    /** A keyframe value must be a number, or a string that is just a number. Anything else is Molang: rejected. */
    private static double number(Object value, String label, String anim, String channel) {
        if (value instanceof Double d) return d;
        if (value instanceof String s && PLAIN_NUMBER.matcher(s).matches()) {
            double d = Double.parseDouble(s.trim());
            if (Double.isFinite(d)) return d;
        }
        String shown = String.valueOf(value);
        if (shown.length() > 40) shown = shown.substring(0, 40) + "...";
        throw new ToolException(label + ": animation '" + anim + "' (" + channel + ") uses the expression \""
            + shown + "\". Only plain numbers are allowed (no Molang): bake it into keyframes in Blockbench.");
    }

    private static String trigger(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot >= 0) n = n.substring(dot + 1);
        return switch (n.trim()) {
            case "idle" -> "idle";
            case "walk", "walking" -> "walk";
            case "sneak", "sneaking", "crouch", "crouching" -> "sneak";
            default -> null;
        };
    }
}
