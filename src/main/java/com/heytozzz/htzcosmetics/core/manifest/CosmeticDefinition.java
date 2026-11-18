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
    AssetRef texture,
    Glow glow,
    Aura aura,
    Tint tint
) {
    /* tint is null when players cannot change the colour. */
    /*
     * model and texture are null only for AURA cosmetics without a model; glow is null when the cosmetic does not
     * glow; aura is non-null exactly for AURA cosmetics.
     */

    /** True if there is a 3D model to draw (everything except model-less auras). */
    public boolean hasModel() {
        return model != null;
    }

    /** Name in the given Minecraft language code (e.g. "es_es"), falling back to English. */
    public String displayName(String languageCode) {
        String name = names.get(languageCode);
        return name != null ? name : names.get("en_us");
    }
}
