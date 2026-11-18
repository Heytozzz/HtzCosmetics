package com.heytozzz.htzcosmetics.core.manifest;

import java.util.List;
import java.util.Map;

/**
 * A validated manifest.
 *
 * @param manifestVersion increases with every publication (anti-rollback)
 * @param expiresAt       epoch seconds after which the manifest is no longer trusted (anti-freeze)
 * @param skippedEntries  entries ignored because they were invalid or need a newer mod
 */
public record Manifest(
    int format,
    long manifestVersion,
    long expiresAt,
    List<CosmeticDefinition> cosmetics,
    Map<String, CosmeticDefinition> byId,
    int skippedEntries
) {
}
