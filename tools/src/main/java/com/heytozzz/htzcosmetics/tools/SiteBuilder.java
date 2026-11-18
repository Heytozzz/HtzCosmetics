package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.crypto.Hashes;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;
import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.Manifest;
import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import com.heytozzz.htzcosmetics.core.png.DecodedImage;
import com.heytozzz.htzcosmetics.core.png.SafePngDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Turns the owner's {@code cosmetics/} folder into a signed, static {@code site/} folder ready for GitHub Pages:
 * <pre>
 * cosmetics/top_hat/cosmetic.json   names, category, rarity, (anchor)
 * cosmetics/top_hat/model.bbmodel   Blockbench model (or model.json already in htzmodel format)
 * cosmetics/top_hat/texture.png     optional if the texture is embedded in the .bbmodel
 *   →
 * site/manifest.json, site/manifest.json.sig, site/a/&lt;sha256&gt;
 * </pre>
 * Every output is re-checked with the mod's own validators before anything is written.
 */
public final class SiteBuilder {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    public record Options(Path cosmeticsDir, Path siteDir, SigningKey key, List<String> trustedKeys,
                          long nowEpochSeconds, int validDays) {
    }

    public record Summary(long manifestVersion, long expiresAt, int cosmetics, List<String> warnings) {
    }

    private record Asset(String sha256, byte[] bytes) {
    }

    private SiteBuilder() {
    }

    public static Summary build(Options o) throws IOException {
        if (!o.trustedKeys().contains(o.key().publicBase64())) {
            throw new ToolException("The signing key is not in TrustedKeys.java, so the mod would reject this manifest.\n"
                + "Run 'gradlew htzKeygen' (it syncs TrustedKeys.java) and rebuild the mod.");
        }
        if (!Files.isDirectory(o.cosmeticsDir())) {
            throw new ToolException("Folder not found: " + o.cosmeticsDir().toAbsolutePath());
        }
        if (o.validDays() < 1 || o.validDays() > 3650) throw new ToolException("--days must be between 1 and 3650");

        List<String> warnings = new ArrayList<>();
        List<Object> entries = new ArrayList<>();
        Map<String, Asset> assets = new LinkedHashMap<>();

        List<Path> folders;
        try (Stream<Path> s = Files.list(o.cosmeticsDir())) {
            folders = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path folder : folders) {
            String id = folder.getFileName().toString();
            if (id.startsWith(".") || id.startsWith("_")) continue; // _drafts etc. are ignored
            entries.add(buildCosmetic(id, folder, assets, warnings));
        }
        if (entries.isEmpty()) warnings.add("no cosmetics found in " + o.cosmeticsDir());
        if (entries.size() > Limits.MAX_COSMETICS) throw new ToolException("Too many cosmetics (max " + Limits.MAX_COSMETICS + ")");

        long previous = previousVersion(o.siteDir().resolve("manifest.json"));
        long version = Math.max(previous + 1, o.nowEpochSeconds());
        long expiresAt = o.nowEpochSeconds() + o.validDays() * 86_400L;

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", ManifestParser.SUPPORTED_MANIFEST_FORMAT);
        manifest.put("manifestVersion", version);
        manifest.put("expiresAt", expiresAt);
        manifest.put("cosmetics", entries);
        byte[] manifestBytes = JsonWriter.toBytes(manifest);
        if (manifestBytes.length > Limits.MAX_MANIFEST_BYTES) throw new ToolException("manifest.json is too large");

        Manifest parsed;
        try {
            parsed = ManifestParser.parseUnverified(manifestBytes);
        } catch (ValidationException e) {
            throw new ToolException("Generated manifest rejected by the mod rules (bug): " + e.getMessage());
        }
        if (parsed.skippedEntries() != 0) throw new ToolException("Generated manifest has rejected entries (bug)");

        byte[] signature = o.key().sign(manifestBytes);
        byte[] sigFile = (Base64.getEncoder().encodeToString(signature) + "\n").getBytes(StandardCharsets.US_ASCII);

        // Write assets first, the signed manifest last, so a half-finished run never points to missing files.
        Path assetDir = o.siteDir().resolve("a");
        Files.createDirectories(assetDir);
        for (Asset a : assets.values()) {
            Path file = assetDir.resolve(a.sha256());
            if (!Files.exists(file)) writeAtomically(file, a.bytes());
        }
        Path noJekyll = o.siteDir().resolve(".nojekyll");
        if (!Files.exists(noJekyll)) Files.write(noJekyll, new byte[0]);
        writeAtomically(o.siteDir().resolve("manifest.json"), manifestBytes);
        writeAtomically(o.siteDir().resolve("manifest.json.sig"), sigFile);

        // Remove asset files no longer referenced (only files named like a hash; nothing else is touched).
        try (Stream<Path> s = Files.list(assetDir)) {
            for (Path f : s.toList()) {
                String name = f.getFileName().toString();
                if (Hashes.isSha256Hex(name) && !assets.containsKey(name)) Files.delete(f);
            }
        }

        return new Summary(version, expiresAt, entries.size(), warnings);
    }

    /**
     * Checks ONE cosmetic folder with exactly the same rules as {@link #build}, without a signing key and without
     * writing anything. Used by HtzCosmetics Studio to report problems per cosmetic.
     *
     * @return non-fatal warnings
     * @throws ToolException with the same message {@link #build} would stop with
     */
    public static List<String> checkCosmetic(String id, Path folder) throws IOException {
        List<String> warnings = new ArrayList<>();
        buildCosmetic(id, folder, new LinkedHashMap<>(), warnings);
        return warnings;
    }

    private static Map<String, Object> buildCosmetic(String id, Path folder, Map<String, Asset> assets,
                                                     List<String> warnings) throws IOException {
        String label = "cosmetics/" + id;
        if (!ID.matcher(id).matches()) {
            throw new ToolException(label + ": folder name must be 1-32 characters of a-z, 0-9 or _ (it is the cosmetic id)");
        }
        Path infoFile = folder.resolve("cosmetic.json");
        if (!Files.isRegularFile(infoFile)) throw new ToolException(label + ": missing cosmetic.json");

        Map<String, Object> info;
        Category category;
        Rarity rarity;
        Anchor anchor;
        Map<String, Object> names;
        try {
            info = Json.asObject(StrictJsonParser.parse(Files.readAllBytes(infoFile), 64 * 1024), "cosmetic.json");
            category = parseEnum(Category.values(), Json.string(info, "category", 32), label, "category");
            rarity = parseEnum(Rarity.values(), Json.string(info, "rarity", 32), label, "rarity");
            anchor = Json.has(info, "anchor")
                ? parseEnum(Anchor.values(), Json.string(info, "anchor", 32), label, "anchor")
                : (category == Category.HEAD ? Anchor.HEAD : Anchor.BODY);
            names = Json.object(info, "name");
        } catch (ValidationException e) {
            throw new ToolException(label + "/cosmetic.json: " + e.getMessage());
        }
        if (!category.allows(anchor)) {
            throw new ToolException(label + ": anchor " + anchor + " is not allowed for category " + category);
        }
        if (!names.containsKey("en_us")) throw new ToolException(label + ": \"name\" needs at least an \"en_us\" entry");

        // --- Model ---
        Path bbmodel = folder.resolve("model.bbmodel");
        Path htzmodel = folder.resolve("model.json");
        if (Files.exists(bbmodel) == Files.exists(htzmodel)) {
            throw new ToolException(label + ": needs exactly one of model.bbmodel or model.json");
        }
        byte[] modelBytes;
        byte[] embeddedTexture = null;
        if (Files.exists(bbmodel)) {
            BbmodelConverter.Result r = BbmodelConverter.convert(Files.readAllBytes(bbmodel), anchor, label);
            modelBytes = r.modelJson();
            embeddedTexture = r.embeddedTexture();
            for (String w : r.warnings()) warnings.add(label + ": " + w);
        } else {
            modelBytes = Files.readAllBytes(htzmodel);
        }
        CosmeticModel model;
        try {
            model = ModelParser.parse(modelBytes);
        } catch (ValidationException e) {
            throw new ToolException(label + ": model rejected by the mod rules: " + e.getMessage()
                + "\n  (limits: " + Limits.MAX_BONES + " groups, " + Limits.MAX_CUBES + " cubes, cubes within "
                + (int) Limits.MAX_COORDINATE + " pixels of their group pivot)");
        }

        // --- Texture ---
        Path textureFile = folder.resolve("texture.png");
        byte[] rawTexture = Files.exists(textureFile) ? Files.readAllBytes(textureFile) : embeddedTexture;
        if (rawTexture == null) throw new ToolException(label + ": no texture.png and no texture embedded in the model");
        byte[] texture = PngNormalizer.normalize(rawTexture, label + " texture");
        DecodedImage image;
        try {
            image = SafePngDecoder.decode(texture);
        } catch (ValidationException e) {
            throw new ToolException(label + ": texture rejected: " + e.getMessage());
        }
        if (image.width() % model.textureWidth() != 0 || image.height() % model.textureHeight() != 0
            || image.width() / model.textureWidth() != image.height() / model.textureHeight()) {
            throw new ToolException(label + ": texture is " + image.width() + "x" + image.height()
                + " but the model expects " + model.textureWidth() + "x" + model.textureHeight() + " (or an exact multiple)");
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", names);
        entry.put("category", category.name());
        entry.put("rarity", rarity.name());
        entry.put("anchor", anchor.name());
        entry.put("minFormat", 1);
        entry.put("model", assetRef(modelBytes, assets));
        entry.put("texture", assetRef(texture, assets));

        // Check this single entry with the mod's own rules for a precise error message.
        Map<String, Object> single = new LinkedHashMap<>();
        single.put("format", 1);
        single.put("manifestVersion", 1);
        single.put("expiresAt", 32_000_000_000L);
        single.put("cosmetics", List.of(entry));
        try {
            if (ManifestParser.parseUnverified(JsonWriter.toBytes(single)).skippedEntries() != 0) {
                throw new ToolException(label + ": rejected by the mod rules. Check the names: language codes like "
                    + "en_us / es_es, max " + Limits.MAX_NAME_LENGTH + " characters, no § or invisible characters.");
            }
        } catch (ValidationException e) {
            throw new ToolException(label + ": " + e.getMessage());
        }
        return entry;
    }

    private static Map<String, Object> assetRef(byte[] bytes, Map<String, Asset> assets) {
        String sha = Hashes.sha256Hex(bytes);
        assets.putIfAbsent(sha, new Asset(sha, bytes));
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("sha256", sha);
        ref.put("size", bytes.length);
        return ref;
    }

    private static <E extends Enum<E>> E parseEnum(E[] values, String text, String label, String field) {
        for (E e : values) {
            if (e.name().equals(text)) return e;
        }
        List<String> allowed = new ArrayList<>();
        for (E e : values) allowed.add(e.name());
        throw new ToolException(label + ": unknown " + field + " '" + text + "'. Allowed: " + String.join(", ", allowed));
    }

    private static long previousVersion(Path manifestFile) {
        if (!Files.exists(manifestFile)) return 0;
        try {
            return ManifestParser.parseUnverified(Files.readAllBytes(manifestFile)).manifestVersion();
        } catch (IOException | ValidationException e) {
            throw new ToolException("Existing " + manifestFile + " is unreadable (" + e.getMessage()
                + "). Fix or delete it; its version is needed to keep versions increasing.");
        }
    }

    private static void writeAtomically(Path file, byte[] bytes) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
