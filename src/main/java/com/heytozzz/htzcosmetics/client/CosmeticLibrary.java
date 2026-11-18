package com.heytozzz.htzcosmetics.client;

import com.heytozzz.htzcosmetics.HtzCosmetics;
import com.heytozzz.htzcosmetics.client.render.BakedCosmetic;
import com.heytozzz.htzcosmetics.client.render.CosmeticBaker;
import com.heytozzz.htzcosmetics.core.source.CosmeticSource;
import com.heytozzz.htzcosmetics.core.AssetCheck;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.crypto.TrustedKeys;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.manifest.Manifest;
import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.png.DecodedImage;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds the verified manifest and the cosmetics ready to render.
 *
 * <p>Threads: reading, signature/hash checks and parsing happen on a background thread; only the final
 * texture upload and model baking run on the render thread (required by OpenGL). Any failure is logged and
 * skipped; the game keeps running with whatever was valid.
 */
public final class CosmeticLibrary {
    private static final CosmeticLibrary INSTANCE = new CosmeticLibrary();

    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HtzCosmetics-loader");
        t.setDaemon(true);
        return t;
    });
    private volatile Manifest manifest;
    private volatile Map<String, BakedCosmetic> baked = Map.of();
    private volatile long lastAcceptedVersion;
    private final Set<ResourceLocation> registeredTextures = new HashSet<>(); // render thread only

    private record Prepared(CosmeticDefinition definition, CosmeticModel model, DecodedImage image) {
    }

    private CosmeticLibrary() {
    }

    public static CosmeticLibrary get() {
        return INSTANCE;
    }

    /** Cosmetic ready to draw, or null. Safe to call every frame. */
    public BakedCosmetic baked(String id) {
        return baked.get(id);
    }

    /** All cosmetics ready to draw, in manifest order. */
    public List<CosmeticDefinition> available() {
        Manifest m = manifest;
        if (m == null) return List.of();
        Map<String, BakedCosmetic> ready = baked;
        List<CosmeticDefinition> out = new ArrayList<>();
        for (CosmeticDefinition def : m.cosmetics()) {
            if (ready.containsKey(def.id())) out.add(def);
        }
        return out;
    }

    public CosmeticDefinition definition(String id) {
        BakedCosmetic b = baked.get(id);
        return b == null ? null : b.definition();
    }

    /**
     * Loads everything from {@code source} in the background and swaps it in when ready.
     *
     * @param force re-install even if the manifest version did not change
     */
    public void reload(CosmeticSource source, boolean force) {
        loader.execute(() -> {
            try {
                if (!TrustedKeys.isConfigured()) {
                    HtzCosmetics.LOGGER.warn("No trusted manifest keys in this build; cosmetics disabled");
                    return;
                }
                long now = System.currentTimeMillis() / 1000L;
                long minVersion = Math.max(lastAcceptedVersion, source.lastAcceptedVersion());
                byte[] manifestBytes = source.manifest();
                byte[] signatureBytes = source.signature();
                Manifest m = ManifestParser.verifyAndParse(manifestBytes, signatureBytes, TrustedKeys.verifier(),
                    now, minVersion);
                try {
                    source.onManifestAccepted(manifestBytes, signatureBytes, m.manifestVersion());
                } catch (Exception e) {
                    HtzCosmetics.LOGGER.warn("Could not cache the cosmetics manifest: {}", e.toString());
                }
                Manifest current = manifest;
                if (!force && current != null && current.manifestVersion() == m.manifestVersion()) {
                    return; // nothing changed since the last check
                }
                if (m.skippedEntries() > 0) {
                    HtzCosmetics.LOGGER.warn("Ignored {} invalid or unsupported cosmetic entries", m.skippedEntries());
                }
                List<Prepared> prepared = new ArrayList<>();
                for (CosmeticDefinition def : m.cosmetics()) {
                    try {
                        CosmeticModel model = AssetCheck.model(
                            source.asset(def.model().sha256(), def.model().size()), def.model());
                        DecodedImage image = AssetCheck.texture(
                            source.asset(def.texture().sha256(), def.texture().size()), def.texture());
                        prepared.add(new Prepared(def, model, image));
                    } catch (Exception e) {
                        HtzCosmetics.LOGGER.warn("Ignoring cosmetic '{}': {}", def.id(), e.getMessage());
                    }
                }
                Minecraft.getInstance().execute(() -> install(m, prepared));
            } catch (ValidationException e) {
                HtzCosmetics.LOGGER.warn("Cosmetics manifest from {} rejected: {}", source.describe(), e.getMessage());
            } catch (Exception e) {
                HtzCosmetics.LOGGER.warn("Could not load cosmetics from {}: {}", source.describe(), e.toString());
            }
        });
    }

    /** Render thread. */
    private void install(Manifest m, List<Prepared> prepared) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        Map<String, BakedCosmetic> next = new HashMap<>();
        Map<String, ResourceLocation> uploadedBySha = new HashMap<>();
        Map<ResourceLocation, Boolean> translucentByTexture = new HashMap<>();

        for (Prepared p : prepared) {
            try {
                String sha = p.definition().texture().sha256();
                ResourceLocation location = uploadedBySha.get(sha);
                if (location == null) {
                    location = ResourceLocation.fromNamespaceAndPath(HtzCosmetics.MOD_ID, "cosmetic/" + sha);
                    String label = "htzcosmetics:" + p.definition().id();
                    textures.register(location, new DynamicTexture(() -> label, toNativeImage(p.image())));
                    uploadedBySha.put(sha, location);
                    translucentByTexture.put(location, hasPartialAlpha(p.image()));
                }
                next.put(p.definition().id(), new BakedCosmetic(p.definition(), CosmeticBaker.bake(p.model()),
                    location, translucentByTexture.get(location)));
            } catch (Exception e) {
                HtzCosmetics.LOGGER.warn("Ignoring cosmetic '{}': {}", p.definition().id(), e.toString());
            }
        }

        for (ResourceLocation old : registeredTextures) {
            if (!uploadedBySha.containsValue(old)) textures.release(old);
        }
        registeredTextures.clear();
        registeredTextures.addAll(uploadedBySha.values());

        manifest = m;
        baked = Map.copyOf(next);
        lastAcceptedVersion = Math.max(lastAcceptedVersion, m.manifestVersion());
        HtzCosmetics.LOGGER.info("Loaded {} cosmetics (manifest version {})", next.size(), m.manifestVersion());
    }

    /** Copies already-validated pixels into a GPU-ready image. Downloaded bytes never reach a native decoder. */
    private static NativeImage toNativeImage(DecodedImage image) {
        NativeImage nativeImage = new NativeImage(image.width(), image.height(), false);
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) nativeImage.setPixel(x, y, image.pixel(x, y));
        }
        return nativeImage;
    }

    private static boolean hasPartialAlpha(DecodedImage image) {
        for (int argb : image.argb()) {
            int alpha = argb >>> 24;
            if (alpha != 0 && alpha != 255) return true;
        }
        return false;
    }
}
