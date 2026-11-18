package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;
import com.heytozzz.htzcosmetics.core.manifest.Anchor;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts a Blockbench {@code .bbmodel} file into our htzmodel v1 JSON.
 *
 * <p><b>How to model:</b> build the cosmetic on top of a normal player (feet at Y=0, head from Y=24 to 32)
 * using a "Modded Entity" / box-UV project. The converter makes everything relative to the anchor body part,
 * so a hat placed on the player's head in Blockbench appears on the head in game.
 *
 * <p>Converted: groups (bones), box-UV cubes, pivots, rotations, inflate, mirror, and animations named
 * idle / walk / sneak as plain keyframes (see {@link BbAnimationConverter}; Molang expressions are rejected).
 * Meshes and per-face UV are rejected with a clear message.
 *
 * <p>Coordinate conversion (same as Blockbench's own Java entity export): Blockbench uses Y up and the
 * opposite X direction, so x and y are negated, z is kept; rotations around X and Y are negated.
 */
public final class BbmodelConverter {
    /** Pivot of each anchor body part, in Blockbench coordinates of a standard player model. */
    static double[] anchorPivot(Anchor anchor) {
        return switch (anchor) {
            case HEAD, BODY -> new double[]{0, 24, 0};
            case RIGHT_ARM -> new double[]{5, 22, 0};
            case LEFT_ARM -> new double[]{-5, 22, 0};
            case RIGHT_LEG -> new double[]{1.9, 12, 0};
            case LEFT_LEG -> new double[]{-1.9, 12, 0};
        };
    }

    /** Result of a conversion. {@code embeddedTexture} is the first texture stored inside the .bbmodel, or null. */
    public record Result(byte[] modelJson, byte[] embeddedTexture, List<String> warnings) {
    }

    private final String label;
    private final double[] anchorPivot;
    private final Map<String, Map<String, Object>> elementsByUuid = new HashMap<>();
    private final Map<String, Map<String, Object>> groupsByUuid = new HashMap<>();
    /** Blockbench group uuid -> our bone name (animations target groups by uuid). */
    private final Map<String, String> boneByGroupUuid = new HashMap<>();
    private final List<Map<String, Object>> bones = new ArrayList<>();
    private final Set<String> usedNames = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean defaultBoxUv;
    private int texWidth;
    private int texHeight;

    private BbmodelConverter(String label, Anchor anchor) {
        this.label = label;
        this.anchorPivot = anchorPivot(anchor);
    }

    public static Result convert(byte[] bbmodel, Anchor anchor, String label) {
        try {
            return new BbmodelConverter(label, anchor).run(bbmodel);
        } catch (ValidationException e) {
            throw new ToolException(label + ": " + e.getMessage());
        }
    }

    private Result run(byte[] bbmodel) throws ValidationException {
        // Owner's own file, but still parsed with our strict parser (just with bigger limits for embedded textures).
        Map<String, Object> root = Json.asObject(StrictJsonParser.parse(bbmodel, 64 << 20,
            256, 64 << 20, 1_000_000, 5_000_000), "bbmodel");

        Map<String, Object> meta = Json.has(root, "meta") ? Json.object(root, "meta") : Map.of();
        defaultBoxUv = Json.boolOr(meta, "box_uv", true);

        if (Json.has(root, "resolution")) {
            Map<String, Object> res = Json.object(root, "resolution");
            texWidth = (int) Json.integer(res, "width", 1, 4096);
            texHeight = (int) Json.integer(res, "height", 1, 4096);
        } else {
            texWidth = 64;
            texHeight = 64;
        }


        if (Json.has(root, "elements")) {
            for (Object e : Json.asArray(root.get("elements"), "elements")) {
                Map<String, Object> el = Json.asObject(e, "element");
                if (Json.has(el, "uuid")) elementsByUuid.put(Json.asString(el.get("uuid"), "uuid", 64), el);
            }
        }
        if (Json.has(root, "groups")) { // Blockbench 5 stores groups separately from the outliner
            for (Object g : Json.asArray(root.get("groups"), "groups")) {
                Map<String, Object> group = Json.asObject(g, "group");
                if (Json.has(group, "uuid")) groupsByUuid.put(Json.asString(group.get("uuid"), "uuid", 64), group);
            }
        }

        List<Object> outliner = Json.has(root, "outliner") ? Json.asArray(root.get("outliner"), "outliner") : List.of();
        List<Map<String, Object>> looseCubes = new ArrayList<>();
        for (Object node : outliner) {
            if (node instanceof String uuid) {
                Map<String, Object> el = elementsByUuid.get(uuid);
                if (el != null) looseCubes.add(el);
            } else {
                convertGroup(Json.asObject(node, "outliner node"), null, null);
            }
        }
        if (!looseCubes.isEmpty()) {
            // Cubes outside any group: attach them to a bone sitting exactly on the anchor pivot.
            Map<String, Object> bone = newBone("root", null, new double[3], new double[3]);
            for (Map<String, Object> el : looseCubes) addElement(bone, "root", el, anchorPivot);
        }
        bones.removeIf(b -> ((List<?>) b.get("cubes")).isEmpty() && !hasChildren(b));
        if (bones.isEmpty()) throw new ToolException(label + ": the model has no visible cubes");

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("format", 1);
        Map<String, Object> texture = new LinkedHashMap<>();
        texture.put("width", texWidth);
        texture.put("height", texHeight);
        model.put("texture", texture);
        model.put("bones", bones);
        List<Object> animations = BbAnimationConverter.convert(root, boneByGroupUuid, label, warnings);
        if (!animations.isEmpty()) model.put("animations", animations);
        return new Result(JsonWriter.toBytes(model), embeddedTexture(root), warnings);
    }

    private boolean hasChildren(Map<String, Object> bone) {
        for (Map<String, Object> b : bones) {
            if (bone.get("name").equals(b.get("parent"))) return true;
        }
        return false;
    }

    /**
     * @param parentName   name of the parent bone, or null for a root bone
     * @param parentOrigin parent pivot in Blockbench coordinates, or null (root: the anchor pivot)
     */
    private void convertGroup(Map<String, Object> node, String parentName, double[] parentOrigin) throws ValidationException {
        Map<String, Object> group = node;
        if (!Json.has(node, "name") && Json.has(node, "uuid")) {
            group = groupsByUuid.get(Json.asString(node.get("uuid"), "uuid", 64));
            if (group == null) return;
        }
        if (!Json.boolOr(group, "export", true) || !Json.boolOr(group, "visibility", true)) {
            warnings.add("skipped hidden/non-exported group '" + Json.asString(group.get("name"), "name", 256) + "'");
            return;
        }

        double[] origin = vec(group, "origin");
        double[] rotation = vec(group, "rotation");
        double[] relative = sub(origin, parentOrigin == null ? anchorPivot : parentOrigin);
        String name = uniqueName(Json.has(group, "name") ? Json.asString(group.get("name"), "name", 256) : "bone");
        String groupUuid = Json.has(node, "uuid") ? Json.asString(node.get("uuid"), "uuid", 64)
            : (Json.has(group, "uuid") ? Json.asString(group.get("uuid"), "uuid", 64) : null);
        if (groupUuid != null) boneByGroupUuid.put(groupUuid, name);
        Map<String, Object> bone = newBone(name, parentName, toJava(relative), toJavaRotation(rotation));

        List<Object> children = Json.has(node, "children") ? Json.asArray(node.get("children"), "children") : List.of();
        List<Map<String, Object>> childGroups = new ArrayList<>();
        for (Object child : children) {
            if (child instanceof String uuid) {
                Map<String, Object> el = elementsByUuid.get(uuid);
                if (el != null) addElement(bone, name, el, origin);
            } else {
                childGroups.add(Json.asObject(child, "group"));
            }
        }
        for (Map<String, Object> child : childGroups) convertGroup(child, name, origin);
    }

    @SuppressWarnings("unchecked")
    private void addElement(Map<String, Object> bone, String boneName, Map<String, Object> el, double[] boneOrigin)
        throws ValidationException {
        String elName = Json.has(el, "name") ? Json.asString(el.get("name"), "name", 256) : "cube";
        String type = Json.has(el, "type") ? Json.asString(el.get("type"), "type", 64) : "cube";
        if (type.equals("locator") || type.equals("null_object")) return;
        if (!type.equals("cube")) {
            throw new ToolException(label + ": element '" + elName + "' is a " + type
                + ". Only cubes are supported (no meshes).");
        }
        if (!Json.boolOr(el, "export", true) || !Json.boolOr(el, "visibility", true)) {
            warnings.add("skipped hidden/non-exported cube '" + elName + "'");
            return;
        }
        boolean boxUv = Json.boolOr(el, "box_uv", defaultBoxUv);
        if (!boxUv) {
            throw new ToolException(label + ": cube '" + elName + "' uses per-face UV. "
                + "Use Box UV (File > Project > Box UV) for every cube.");
        }

        double[] from = vec(el, "from");
        double[] to = vec(el, "to");
        double[] cubeOrigin = vec(el, "origin");
        double[] cubeRotation = vec(el, "rotation");

        Map<String, Object> target = bone;
        double[] localOrigin = boneOrigin;
        if (cubeRotation[0] != 0 || cubeRotation[1] != 0 || cubeRotation[2] != 0) {
            // Our format rotates bones, not single cubes: wrap a rotated cube in its own child bone.
            String childName = uniqueName(boneName + "_" + elName);
            target = newBone(childName, boneName, toJava(sub(cubeOrigin, boneOrigin)), toJavaRotation(cubeRotation));
            localOrigin = cubeOrigin;
        }

        double[] uv = Json.has(el, "uv_offset") ? Json.numbers(el, "uv_offset", 2, 0, 4096, null) : new double[2];
        if (uv[0] != Math.rint(uv[0]) || uv[1] != Math.rint(uv[1])) {
            throw new ToolException(label + ": cube '" + elName + "' has a non-integer UV offset");
        }

        Map<String, Object> cube = new LinkedHashMap<>();
        // Java origin = (-(to.x - o.x), -(to.y - o.y), from.z - o.z)
        cube.put("origin", new double[]{-(to[0] - localOrigin[0]), -(to[1] - localOrigin[1]), from[2] - localOrigin[2]});
        cube.put("size", new double[]{to[0] - from[0], to[1] - from[1], to[2] - from[2]});
        cube.put("uv", List.of((int) uv[0], (int) uv[1]));
        double inflate = Json.numberOr(el, "inflate", -1000, 1000, 0);
        if (inflate != 0) cube.put("inflate", inflate);
        if (Json.boolOr(el, "mirror_uv", false)) cube.put("mirror", true);
        ((List<Object>) target.get("cubes")).add(cube);
    }

    private Map<String, Object> newBone(String name, String parent, double[] pivot, double[] rotation) {
        Map<String, Object> bone = new LinkedHashMap<>();
        bone.put("name", name);
        if (parent != null) bone.put("parent", parent);
        bone.put("pivot", pivot);
        bone.put("rotation", rotation);
        bone.put("cubes", new ArrayList<>());
        bones.add(bone);
        return bone;
    }

    private String uniqueName(String raw) {
        String base = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (base.isEmpty()) base = "bone";
        if (base.length() > 28) base = base.substring(0, 28);
        String name = base;
        for (int i = 2; usedNames.contains(name); i++) name = base + "_" + i;
        usedNames.add(name);
        return name;
    }

    private byte[] embeddedTexture(Map<String, Object> root) throws ValidationException {
        if (!Json.has(root, "textures")) return null;
        List<Object> textures = Json.asArray(root.get("textures"), "textures");
        if (textures.isEmpty()) return null;
        if (textures.size() > 1) warnings.add("the model has " + textures.size() + " textures; only the first one is used");
        Map<String, Object> tex = Json.asObject(textures.get(0), "texture");
        if (!Json.has(tex, "source")) return null;
        String source = Json.asString(tex.get("source"), "source", 64 << 20);
        String prefix = "data:image/png;base64,";
        if (!source.startsWith(prefix)) return null;
        try {
            return Base64.getDecoder().decode(source.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw new ToolException(label + ": embedded texture is not valid Base64");
        }
    }

    private static double[] vec(Map<String, Object> obj, String key) throws ValidationException {
        return Json.numbers(obj, key, 3, -100_000, 100_000, new double[3]);
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] toJava(double[] v) {
        return new double[]{-v[0], -v[1], v[2]};
    }

    private static double[] toJavaRotation(double[] r) {
        return new double[]{-r[0], -r[1], r[2]};
    }
}
