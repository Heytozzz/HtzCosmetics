package com.heytozzz.htzcosmetics.core.model;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses the HtzCosmetics model format ("htzmodel", format 1). Pure data: no expressions, no scripts.
 *
 * <pre>{@code
 * {
 *   "format": 1,
 *   "texture": { "width": 64, "height": 64 },
 *   "bones": [
 *     { "name": "hat", "parent": null, "pivot": [0, 0, 0], "rotation": [0, 0, 0],
 *       "cubes": [ { "origin": [-4, -16, -4], "size": [8, 8, 8], "uv": [0, 0], "inflate": 0.5, "mirror": false } ] }
 *   ]
 * }
 * }</pre>
 * Unknown keys (e.g. a future "animations" block) are ignored.
 */
public final class ModelParser {
    public static final int SUPPORTED_FORMAT = 1;
    private static final Pattern BONE_NAME = Pattern.compile("[A-Za-z0-9_]{1,32}");

    private ModelParser() {
    }

    public static CosmeticModel parse(byte[] json) throws ValidationException {
        Map<String, Object> root = Json.asObject(StrictJsonParser.parse(json, Limits.MAX_MODEL_BYTES), "model");

        int format = (int) Json.integer(root, "format", 1, 1000);
        if (format > SUPPORTED_FORMAT) throw new ValidationException("model format " + format + " not supported");

        Map<String, Object> texture = Json.object(root, "texture");
        int texWidth = (int) Json.integer(texture, "width", 1, Limits.MAX_TEXTURE_DIMENSION);
        int texHeight = (int) Json.integer(texture, "height", 1, Limits.MAX_TEXTURE_DIMENSION);

        List<Object> boneList = Json.array(root, "bones", Limits.MAX_BONES);
        if (boneList.isEmpty()) throw new ValidationException("model has no bones");

        List<Bone> bones = new ArrayList<>();
        Map<String, Integer> indexByName = new HashMap<>();
        int totalCubes = 0;

        for (Object boneValue : boneList) {
            Map<String, Object> b = Json.asObject(boneValue, "bone");

            String name = Json.string(b, "name", 32);
            if (!BONE_NAME.matcher(name).matches()) throw new ValidationException("invalid bone name");
            if (indexByName.containsKey(name)) throw new ValidationException("duplicate bone '" + name + "'");

            int parentIndex = -1;
            if (Json.has(b, "parent")) {
                String parent = Json.string(b, "parent", 32);
                Integer idx = indexByName.get(parent);
                // Parents must be declared earlier: this makes cycles impossible.
                if (idx == null) throw new ValidationException("bone '" + name + "' has unknown or later parent");
                parentIndex = idx;
            }

            double[] pivot = Json.numbers(b, "pivot", 3, -Limits.MAX_COORDINATE, Limits.MAX_COORDINATE, new double[3]);
            double[] rotation = Json.numbers(b, "rotation", 3, -Limits.MAX_ROTATION_DEGREES, Limits.MAX_ROTATION_DEGREES,
                new double[3]);

            List<Cube> cubes = new ArrayList<>();
            if (Json.has(b, "cubes")) {
                for (Object cubeValue : Json.array(b, "cubes", Limits.MAX_CUBES)) {
                    if (++totalCubes > Limits.MAX_CUBES) throw new ValidationException("too many cubes");
                    cubes.add(parseCube(Json.asObject(cubeValue, "cube"), texWidth, texHeight));
                }
            }

            indexByName.put(name, bones.size());
            bones.add(new Bone(name, parentIndex, pivot, rotation, Collections.unmodifiableList(cubes)));
        }
        if (totalCubes == 0) throw new ValidationException("model has no cubes");

        return new CosmeticModel(format, texWidth, texHeight, Collections.unmodifiableList(bones), totalCubes);
    }

    private static Cube parseCube(Map<String, Object> c, int texWidth, int texHeight) throws ValidationException {
        double max = Limits.MAX_COORDINATE;
        double[] origin = Json.numbers(c, "origin", 3, -max, max, null);
        double[] size = Json.numbers(c, "size", 3, 0, Limits.MAX_CUBE_SIZE, null);
        for (int i = 0; i < 3; i++) {
            if (origin[i] + size[i] > max) throw new ValidationException("cube exceeds the allowed volume");
        }

        double[] uv = Json.numbers(c, "uv", 2, 0, Limits.MAX_TEXTURE_DIMENSION, null);
        if (uv[0] != Math.rint(uv[0]) || uv[1] != Math.rint(uv[1])) throw new ValidationException("uv must be integers");
        int u = (int) uv[0];
        int v = (int) uv[1];

        // Box UV footprint: (2*depth + 2*width) x (depth + height) pixels must fit inside the texture.
        double w = size[0];
        double h = size[1];
        double d = size[2];
        if (u + Math.ceil(2 * (w + d)) > texWidth || v + Math.ceil(d + h) > texHeight) {
            throw new ValidationException("cube UV is outside the texture");
        }

        double inflate = Json.numberOr(c, "inflate", -Limits.MAX_INFLATE, Limits.MAX_INFLATE, 0);
        boolean mirror = Json.boolOr(c, "mirror", false);
        return new Cube(origin, size, u, v, inflate, mirror);
    }
}
