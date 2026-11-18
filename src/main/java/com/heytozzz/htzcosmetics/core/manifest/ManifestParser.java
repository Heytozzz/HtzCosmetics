package com.heytozzz.htzcosmetics.core.manifest;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import com.heytozzz.htzcosmetics.core.crypto.SignatureVerifier;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Verifies and parses the cosmetics manifest.
 *
 * <p>Expected JSON (format 1):
 * <pre>{@code
 * {
 *   "format": 1,
 *   "manifestVersion": 7,
 *   "expiresAt": 1798761600,
 *   "cosmetics": [
 *     {
 *       "id": "top_hat",
 *       "name": { "en_us": "Top hat", "es_es": "Chistera" },
 *       "category": "HEAD", "rarity": "RARE", "anchor": "HEAD", "minFormat": 1,
 *       "model":   { "sha256": "<64 hex>", "size": 1830 },
 *       "texture": { "sha256": "<64 hex>", "size": 912 },
 *       "glow": { ... }   (optional, see Glow; minFormat 2)
 *     }
 *     (AURA cosmetics, minFormat 2: "aura": { ... } and "glow" required, "model"/"texture" optional together)
 *   ]
 * }
 * }</pre>
 *
 * <p>Whole-manifest problems (bad signature, unsupported format, expired, rollback) reject everything.
 * Problems in a single cosmetic only skip that cosmetic.
 */
public final class ManifestParser {
    /** Highest manifest structure version this build understands. */
    public static final int SUPPORTED_MANIFEST_FORMAT = 1;
    /**
     * Highest cosmetic content format this build understands.
     * 2 added: clothing categories and AURA, per-group body parts in models, "glow" and "aura" blocks.
     */
    public static final int SUPPORTED_COSMETIC_FORMAT = 2;

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}_[a-z]{2,3}");
    private static final long MAX_EPOCH_SECONDS = 32_503_680_000L; // year 3000

    private ManifestParser() {
    }

    /**
     * Full check: signature, structure, expiry and anti-rollback.
     *
     * @param lastAcceptedVersion highest manifestVersion accepted before (0 if none)
     */
    public static Manifest verifyAndParse(byte[] manifestBytes, byte[] signatureFileBytes, SignatureVerifier verifier,
                                          long nowEpochSeconds, long lastAcceptedVersion) throws ValidationException {
        if (manifestBytes == null || manifestBytes.length > Limits.MAX_MANIFEST_BYTES) {
            throw new ValidationException("manifest missing or too large");
        }
        byte[] signature = SignatureVerifier.decodeSignatureFile(signatureFileBytes);
        if (!verifier.verify(manifestBytes, signature)) throw new ValidationException("manifest signature is invalid");

        Manifest manifest = parseUnverified(manifestBytes);
        if (manifest.expiresAt() <= nowEpochSeconds) throw new ValidationException("manifest has expired");
        if (manifest.manifestVersion() < lastAcceptedVersion) {
            throw new ValidationException("manifest version " + manifest.manifestVersion()
                + " is older than already accepted " + lastAcceptedVersion);
        }
        return manifest;
    }

    /** Parses WITHOUT checking the signature. Only for tests and for tools that already verified it. */
    public static Manifest parseUnverified(byte[] manifestBytes) throws ValidationException {
        Map<String, Object> root = Json.asObject(StrictJsonParser.parse(manifestBytes, Limits.MAX_MANIFEST_BYTES), "manifest");

        int format = (int) Json.integer(root, "format", 1, 1000);
        if (format > SUPPORTED_MANIFEST_FORMAT) {
            throw new ValidationException("manifest format " + format + " not supported (update the mod)");
        }
        long version = Json.integer(root, "manifestVersion", 1, Long.MAX_VALUE);
        long expiresAt = Json.integer(root, "expiresAt", 1, MAX_EPOCH_SECONDS);
        List<Object> entries = Json.array(root, "cosmetics", Limits.MAX_COSMETICS);

        List<CosmeticDefinition> cosmetics = new ArrayList<>();
        Map<String, CosmeticDefinition> byId = new LinkedHashMap<>();
        int skipped = 0;
        for (Object entry : entries) {
            CosmeticDefinition def = tryParseCosmetic(entry);
            if (def == null || byId.containsKey(def.id())) {
                skipped++;
                continue;
            }
            cosmetics.add(def);
            byId.put(def.id(), def);
        }
        return new Manifest(format, version, expiresAt, Collections.unmodifiableList(cosmetics),
            Collections.unmodifiableMap(byId), skipped);
    }

    /** Returns null (skip) if the entry is invalid or needs a newer mod. */
    private static CosmeticDefinition tryParseCosmetic(Object entry) {
        try {
            Map<String, Object> obj = Json.asObject(entry, "cosmetic");

            String id = Json.string(obj, "id", 32);
            if (!ID.matcher(id).matches()) return null;

            int minFormat = (int) Json.integer(obj, "minFormat", 1, 1000);
            if (minFormat > SUPPORTED_COSMETIC_FORMAT) return null; // newer content: silently ignored

            Category category = enumValue(Category.values(), Json.string(obj, "category", 32));
            Rarity rarity = enumValue(Rarity.values(), Json.string(obj, "rarity", 32));
            Anchor anchor = enumValue(Anchor.values(), Json.string(obj, "anchor", 32));
            if (category == null || rarity == null || anchor == null || !category.allows(anchor)) return null;

            // Content newer than format 1 must say so, so that older builds skip it instead of misreading it.
            if (minFormat < 2 && (!category.inFormat1() || Json.has(obj, "glow") || Json.has(obj, "aura"))) return null;

            Map<String, String> names = parseNames(Json.object(obj, "name"));
            boolean aura = category.isAura();
            AssetRef model = null;
            AssetRef texture = null;
            if (!aura || Json.has(obj, "model") || Json.has(obj, "texture")) {
                model = parseAsset(Json.object(obj, "model"), Limits.MAX_MODEL_BYTES);
                texture = parseAsset(Json.object(obj, "texture"), Limits.MAX_TEXTURE_BYTES);
            }
            Glow glow = Json.has(obj, "glow") ? Glow.parse(Json.object(obj, "glow")) : null;
            Aura auraSpec = null;
            if (aura) {
                if (glow == null) return null; // an aura's colours come from its glow
                auraSpec = Json.has(obj, "aura") ? Aura.parse(Json.object(obj, "aura")) : new Aura(0.2, 0.5);
            } else if (Json.has(obj, "aura")) {
                return null;
            }

            return new CosmeticDefinition(id, names, category, rarity, anchor, minFormat, model, texture, glow, auraSpec);
        } catch (ValidationException e) {
            return null;
        }
    }

    private static Map<String, String> parseNames(Map<String, Object> obj) throws ValidationException {
        if (obj.size() > Limits.MAX_NAME_LANGUAGES) throw new ValidationException("too many name languages");
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (!LANGUAGE.matcher(e.getKey()).matches()) throw new ValidationException("bad language code");
            String name = Json.asString(e.getValue(), "name", Limits.MAX_NAME_LENGTH);
            if (name.isBlank() || !isPlainText(name)) throw new ValidationException("bad name text");
            names.put(e.getKey(), name);
        }
        if (!names.containsKey("en_us")) throw new ValidationException("English name (en_us) is required");
        return Collections.unmodifiableMap(names);
    }

    /** Rejects control characters and Minecraft's legacy formatting code character. */
    private static boolean isPlainText(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '§' || (c >= 0x80 && c < 0xA0)) return false;
            if (Character.getType(c) == Character.FORMAT) return false; // invisible bidi/format chars
        }
        return true;
    }

    private static AssetRef parseAsset(Map<String, Object> obj, int maxSize) throws ValidationException {
        String sha = Json.string(obj, "sha256", 64);
        if (!Hashes.isSha256Hex(sha)) throw new ValidationException("bad sha256");
        int size = (int) Json.integer(obj, "size", 1, maxSize);
        return new AssetRef(sha, size);
    }

    private static <E extends Enum<E>> E enumValue(E[] constants, String name) {
        // Compares against the fixed, compiled-in constants only. Unknown values -> null (skip).
        for (E constant : constants) {
            if (constant.name().equals(name)) return constant;
        }
        return null;
    }
}
