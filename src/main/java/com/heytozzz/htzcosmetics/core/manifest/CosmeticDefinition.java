package com.heytozzz.htzcosmetics.core.manifest;

import java.util.Map;

/** One cosmetic as described by the signed manifest. All fields are already validated. */
public record CosmeticDefinition(
    String id,
    Map<String, String> names,
    Category category,
    Rarity rarity,
    Anchor anchor,
    int minFormat,
    AssetRef model,
    AssetRef texture
) {
    /** Name in the given Minecraft language code (e.g. "es_es"), falling back to English. */
    public String displayName(String languageCode) {
        String name = names.get(languageCode);
        return name != null ? name : names.get("en_us");
    }
}
