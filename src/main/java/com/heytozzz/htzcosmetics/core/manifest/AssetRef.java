package com.heytozzz.htzcosmetics.core.manifest;

/**
 * Reference to a downloadable file. The file is fetched from {@code <assets base URL>/<sha256>}
 * and stored on disk under its hash, so remote data never controls a path.
 */
public record AssetRef(String sha256, int size) {
}
