package com.heytozzz.htzcosmetics.client.render;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.core.manifest.Glow;
import com.heytozzz.htzcosmetics.core.manifest.Tint;
import com.heytozzz.htzcosmetics.core.png.DecodedImage;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Coloured copies of cosmetic textures, made on demand the first time a colour is needed and kept for reuse
 * (at most {@link #MAX_TEXTURES}; the least recently used is released). Render thread only.
 *
 * <p>Built from the already validated pixels: grey parts take the colour ({@link Tint#apply}), others stay.
 */
public final class TintCache {
    private static final int MAX_TEXTURES = 64;
    private static final Map<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, Glow> GLOWS = new HashMap<>();
    private static int counter;

    private TintCache() {
    }

    /** The texture to draw {@code c} with for a requested colour (null = default); the plain one if not tintable. */
    public static ResourceLocation texture(BakedCosmetic c, Integer requested) {
        Tint tint = c.definition().tint();
        DecodedImage source = c.tintSource();
        if (tint == null || source == null) return c.texture();
        int rgb = tint.effective(requested);
        String key = c.definition().id() + ":" + c.texture().getPath() + ":" + Integer.toHexString(rgb);
        ResourceLocation cached = TEXTURES.get(key);
        if (cached != null) return cached;
        try {
            NativeImage image = new NativeImage(source.width(), source.height(), false);
            for (int y = 0; y < source.height(); y++) {
                for (int x = 0; x < source.width(); x++) image.setPixel(x, y, Tint.apply(source.pixel(x, y), rgb));
            }
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID,
                "tinted/" + (counter++) + "_" + String.format(Locale.ROOT, "%06x", rgb));
            String label = "htzcosmetics:" + c.definition().id() + " tinted";
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(() -> label, image));
            TEXTURES.put(key, location);
            if (TEXTURES.size() > MAX_TEXTURES) {
                var eldest = TEXTURES.entrySet().iterator().next();
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                TEXTURES.remove(eldest.getKey());
            }
            return location;
        } catch (Exception e) {
            HtzCosmetics.LOGGER.warn("Could not colour cosmetic '{}': {}", c.definition().id(), e.toString());
            return c.texture();
        }
    }

    /**
     * The glow to draw: the cosmetic's own, or a single-colour copy when the player chose a colour that the
     * cosmetic allows and its tint says the glow follows the colour.
     */
    public static Glow glow(BakedCosmetic c, Integer requested) {
        Glow glow = c.definition().glow();
        Tint tint = c.definition().tint();
        if (glow == null || tint == null || !tint.glow() || requested == null || !tint.allows(requested)) return glow;
        String key = c.definition().id() + ":" + Integer.toHexString(requested);
        Glow cached = GLOWS.get(key);
        if (cached == null) {
            cached = new Glow(new int[]{requested}, glow.cycle(), glow.emissive(), glow.bloom(), glow.radius(), glow.pulse());
            if (GLOWS.size() > 256) GLOWS.clear();
            GLOWS.put(key, cached);
        }
        return cached;
    }

    /** Releases every coloured texture (cosmetics were reloaded). */
    public static void clear() {
        for (ResourceLocation location : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(location);
        TEXTURES.clear();
        GLOWS.clear();
    }
}
