package com.heytozzz.htzcosmetics.core.json;

import com.heytozzz.htzcosmetics.core.ValidationException;

import java.util.List;
import java.util.Map;

/**
 * Typed, range-checked accessors for values produced by {@link StrictJsonParser}.
 * Unknown keys are simply never read, so they are harmless.
 */
public final class Json {
    private Json() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value, String what) throws ValidationException {
        if (!(value instanceof Map)) throw new ValidationException(what + " must be an object");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value, String what) throws ValidationException {
        if (!(value instanceof List)) throw new ValidationException(what + " must be an array");
        return (List<Object>) value;
    }

    public static Object require(Map<String, Object> obj, String key) throws ValidationException {
        Object value = obj.get(key);
        if (value == null || value == StrictJsonParser.NULL) throw new ValidationException("missing '" + key + "'");
        return value;
    }

    public static boolean has(Map<String, Object> obj, String key) {
        Object value = obj.get(key);
        return value != null && value != StrictJsonParser.NULL;
    }

    public static Map<String, Object> object(Map<String, Object> obj, String key) throws ValidationException {
        return asObject(require(obj, key), "'" + key + "'");
    }

    public static List<Object> array(Map<String, Object> obj, String key, int maxSize) throws ValidationException {
        List<Object> list = asArray(require(obj, key), "'" + key + "'");
        if (list.size() > maxSize) throw new ValidationException("'" + key + "' has too many entries");
        return list;
    }

    public static String string(Map<String, Object> obj, String key, int maxLength) throws ValidationException {
        return asString(require(obj, key), "'" + key + "'", maxLength);
    }

    public static String asString(Object value, String what, int maxLength) throws ValidationException {
        if (!(value instanceof String)) throw new ValidationException(what + " must be a string");
        String s = (String) value;
        if (s.length() > maxLength) throw new ValidationException(what + " is too long");
        return s;
    }

    public static double number(Map<String, Object> obj, String key, double min, double max) throws ValidationException {
        return asNumber(require(obj, key), "'" + key + "'", min, max);
    }

    public static double numberOr(Map<String, Object> obj, String key, double min, double max, double fallback)
        throws ValidationException {
        return has(obj, key) ? number(obj, key, min, max) : fallback;
    }

    public static double asNumber(Object value, String what, double min, double max) throws ValidationException {
        if (!(value instanceof Double)) throw new ValidationException(what + " must be a number");
        double d = (Double) value;
        if (!Double.isFinite(d) || d < min || d > max) {
            throw new ValidationException(what + " out of range [" + min + ", " + max + "]");
        }
        return d;
    }

    public static long integer(Map<String, Object> obj, String key, long min, long max) throws ValidationException {
        return asInteger(require(obj, key), "'" + key + "'", min, max);
    }

    public static long asInteger(Object value, String what, long min, long max) throws ValidationException {
        // Doubles represent integers exactly up to 2^53; we never allow more than that.
        double d = asNumber(value, what, Math.max(min, -9.007199254740991E15), Math.min(max, 9.007199254740991E15));
        if (d != Math.rint(d)) throw new ValidationException(what + " must be an integer");
        return (long) d;
    }

    public static boolean boolOr(Map<String, Object> obj, String key, boolean fallback) throws ValidationException {
        if (!has(obj, key)) return fallback;
        Object value = obj.get(key);
        if (!(value instanceof Boolean)) throw new ValidationException("'" + key + "' must be true or false");
        return (Boolean) value;
    }

    /** Reads a fixed-size array of numbers, e.g. a 3D vector. */
    public static double[] numbers(Map<String, Object> obj, String key, int size, double min, double max,
                                   double[] fallback) throws ValidationException {
        if (!has(obj, key)) {
            if (fallback == null) throw new ValidationException("missing '" + key + "'");
            return fallback.clone();
        }
        List<Object> list = asArray(obj.get(key), "'" + key + "'");
        if (list.size() != size) throw new ValidationException("'" + key + "' must have " + size + " numbers");
        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = asNumber(list.get(i), "'" + key + "'[" + i + "]", min, max);
        return result;
    }
}
