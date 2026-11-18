package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;
import com.heytozzz.htzcosmetics.core.manifest.Anchor;
import com.heytozzz.htzcosmetics.core.manifest.Category;
import com.heytozzz.htzcosmetics.core.manifest.Rarity;
import com.heytozzz.htzcosmetics.core.model.CosmeticModel;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import com.heytozzz.htzcosmetics.tools.BbmodelConverter;
import com.heytozzz.htzcosmetics.tools.JsonWriter;
import com.heytozzz.htzcosmetics.tools.SiteBuilder;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads and writes the {@code cosmetics/<id>/} folders. Rules are always checked by {@link SiteBuilder}. */
final class CosmeticStore {
    /** Same pattern as SiteBuilder.ID (the folder name is the cosmetic id). */
    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    static final List<String> LANGS = List.of("en_us", "es_es", "es_mx");

    /** Contents of cosmetic.json. {@code anchor} is null when the file has none (category default). */
    record Info(Map<String, String> names, Category category, Rarity rarity, Anchor anchor) {
        Anchor effectiveAnchor() {
            return anchor != null ? anchor : category == Category.HEAD ? Anchor.HEAD : Anchor.BODY;
        }
    }

    /** A model ready to draw (converted exactly like the site builder converts it). */
    record Loaded(CosmeticModel model, BufferedImage texture) {
    }

    /** One cosmetic folder. {@code problem} (Spanish) is set when it could not be read or drawn. */
    record Entry(String id, Path folder, Info info, Loaded loaded, String problem) {
        String displayName() {
            if (info == null) return id;
            String es = info.names().get("es_es");
            return es != null ? es : info.names().getOrDefault("en_us", id);
        }

        boolean animated() {
            return loaded != null && !loaded.model().animations().isEmpty();
        }
    }

    private CosmeticStore() {
    }

    static List<Entry> loadAll(Path cosmeticsDir) {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(cosmeticsDir)) return out;
        List<Path> folders;
        try (Stream<Path> s = Files.list(cosmeticsDir)) {
            folders = s.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName)).toList();
        } catch (IOException e) {
            return out;
        }
        for (Path folder : folders) {
            String id = folder.getFileName().toString();
            if (id.startsWith(".") || id.startsWith("_")) continue; // ignored by htzBuildSite too
            out.add(load(id, folder));
        }
        return out;
    }

    static Entry load(String id, Path folder) {
        Info info;
        try {
            info = readInfo(folder);
        } catch (Exception e) {
            return new Entry(id, folder, null, null, ErrorText.translate(e.getMessage()));
        }
        try {
            return new Entry(id, folder, info, loadFolderModel(folder, info.effectiveAnchor()), null);
        } catch (Exception e) {
            return new Entry(id, folder, info, null, ErrorText.translate(e.getMessage()));
        }
    }

    static Info readInfo(Path folder) throws IOException, ValidationException {
        Path file = folder.resolve("cosmetic.json");
        if (!Files.isRegularFile(file)) throw new IOException("missing cosmetic.json");
        Map<String, Object> obj = Json.asObject(StrictJsonParser.parse(Files.readAllBytes(file), 64 * 1024), "cosmetic.json");
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : Json.object(obj, "name").entrySet()) {
            names.put(e.getKey(), Json.asString(e.getValue(), "name", 256));
        }
        Category category = Category.valueOf(Json.string(obj, "category", 32));
        Rarity rarity = Rarity.valueOf(Json.string(obj, "rarity", 32));
        Anchor anchor = Json.has(obj, "anchor") ? Anchor.valueOf(Json.string(obj, "anchor", 32)) : null;
        return new Info(names, category, rarity, anchor);
    }

    static void writeInfo(Path folder, Info info) throws IOException {
        Map<String, Object> obj = new LinkedHashMap<>();
        Map<String, Object> names = new LinkedHashMap<>();
        for (String lang : LANGS) {
            String n = info.names().get(lang);
            if (n != null && !n.isBlank()) names.put(lang, n.strip());
        }
        // Keep any extra languages the owner added by hand.
        info.names().forEach((k, v) -> {
            if (!names.containsKey(k) && v != null && !v.isBlank()) names.put(k, v.strip());
        });
        obj.put("name", names);
        obj.put("category", info.category().name());
        obj.put("rarity", info.rarity().name());
        if (info.anchor() != null) obj.put("anchor", info.anchor().name());
        Files.write(folder.resolve("cosmetic.json"), JsonWriter.toBytes(obj));
    }

    static Loaded loadFolderModel(Path folder, Anchor anchor) throws IOException, ValidationException {
        Path bb = folder.resolve("model.bbmodel");
        Path json = folder.resolve("model.json");
        Path tex = folder.resolve("texture.png");
        byte[] model = Files.exists(bb) ? Files.readAllBytes(bb) : Files.exists(json) ? Files.readAllBytes(json) : null;
        if (model == null) throw new IOException("needs exactly one of model.bbmodel or model.json");
        return loadModel(model, Files.exists(bb), anchor, Files.exists(tex) ? Files.readAllBytes(tex) : null);
    }

    /** Converts a model for drawing. Throws with the same messages as the site builder. */
    static Loaded loadModel(byte[] modelBytes, boolean bbmodel, Anchor anchor, byte[] texturePng)
        throws IOException, ValidationException {
        byte[] json = modelBytes;
        byte[] embedded = null;
        if (bbmodel) {
            BbmodelConverter.Result r = BbmodelConverter.convert(modelBytes, anchor, "model");
            json = r.modelJson();
            embedded = r.embeddedTexture();
        }
        CosmeticModel model = ModelParser.parse(json);
        byte[] png = texturePng != null ? texturePng : embedded;
        BufferedImage image = null;
        if (png != null) image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("no texture.png and no texture embedded in the model");
        return new Loaded(model, image);
    }

    /** Problems of one folder in plain Spanish, using exactly the rules of htzBuildSite (empty = OK). */
    static List<String> check(String id, Path folder) {
        try {
            SiteBuilder.checkCosmetic(id, folder);
            return List.of();
        } catch (Exception e) {
            return List.of(ErrorText.translate(e.getMessage()));
        }
    }

    /** Moves the folder to the Windows recycle bin (falls back to deleting it). @return true if it went to the bin */
    static boolean delete(Path folder) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)
            && Desktop.getDesktop().moveToTrash(folder.toFile())) {
            return true;
        }
        try (Stream<Path> s = Files.walk(folder)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
        return false;
    }
}
