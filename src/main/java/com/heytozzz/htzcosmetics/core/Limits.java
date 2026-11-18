package com.heytozzz.htzcosmetics.core;

/** Hard limits for every piece of downloaded data. Anything above them is rejected. */
public final class Limits {
    // --- Files ---
    public static final int MAX_MANIFEST_BYTES = 512 * 1024;
    public static final int MAX_SIGNATURE_FILE_BYTES = 256;
    public static final int MAX_MODEL_BYTES = 128 * 1024;
    public static final int MAX_TEXTURE_BYTES = 256 * 1024;

    // --- JSON structure ---
    public static final int MAX_JSON_DEPTH = 32;
    public static final int MAX_JSON_STRING_LENGTH = 256;
    public static final int MAX_JSON_CONTAINER_ENTRIES = 4096;
    public static final int MAX_JSON_TOTAL_VALUES = 100_000;

    // --- Manifest ---
    public static final int MAX_COSMETICS = 2000;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_NAME_LANGUAGES = 16;

    // --- Textures ---
    public static final int MAX_TEXTURE_DIMENSION = 256;
    public static final int MAX_PNG_CHUNKS = 1024;

    // --- Models (units = model pixels; a player head is 8 pixels wide) ---
    public static final int MAX_BONES = 32;
    public static final int MAX_CUBES = 256;
    public static final double MAX_COORDINATE = 32.0;
    public static final double MAX_CUBE_SIZE = 32.0;
    public static final double MAX_INFLATE = 4.0;
    public static final double MAX_ROTATION_DEGREES = 360.0;

    private Limits() {
    }
}
