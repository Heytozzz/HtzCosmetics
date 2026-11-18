package com.heytozzz.htzcosmetics.core.config;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Player options, stored in {@code config/htzcosmetics.json}. A damaged or hand-edited file never breaks the game:
 * unknown keys are ignored and anything invalid falls back to the default.
 */
public final class ClientConfig {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final int MAX_FILE_BYTES = 16 * 1024;

    /** See other players' cosmetics. */
    public boolean showOthers = true;
    /** See your own cosmetics (third person, inventory, menus). */
    public boolean showOwn = true;
    /** Draw your own cosmetics in first person too (only matters with first-person body mods). */
    public boolean showOwnFirstPerson = false;
    /** Tell nearby players right away when you change cosmetics. */
    public boolean sendChangeSignal = true;
    /** React right away when nearby players change cosmetics. */
    public boolean receiveChangeSignal = true;
    /** Last known own selection, used until the backend answers (or when offline). */
    public List<String> equipped = new ArrayList<>();

    public static ClientConfig load(Path file) {
        ClientConfig config = new ClientConfig();
        if (!Files.isRegularFile(file)) return config;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] data = in.readNBytes(MAX_FILE_BYTES + 1);
            if (data.length > MAX_FILE_BYTES) return config;
            var root = Json.asObject(StrictJsonParser.parse(data, MAX_FILE_BYTES), "config");
            config.showOthers = Json.boolOr(root, "showOthers", config.showOthers);
            config.showOwn = Json.boolOr(root, "showOwn", config.showOwn);
            config.showOwnFirstPerson = Json.boolOr(root, "showOwnFirstPerson", config.showOwnFirstPerson);
            config.sendChangeSignal = Json.boolOr(root, "sendChangeSignal", config.sendChangeSignal);
            config.receiveChangeSignal = Json.boolOr(root, "receiveChangeSignal", config.receiveChangeSignal);
            if (Json.has(root, "equipped")) {
                for (Object v : Json.array(root, "equipped", 8)) {
                    if (v instanceof String id && ID.matcher(id).matches() && !config.equipped.contains(id)) {
                        config.equipped.add(id);
                    }
                }
            }
        } catch (IOException | ValidationException | RuntimeException e) {
            return new ClientConfig(); // broken file: defaults
        }
        return config;
    }

    public void save(Path file) throws IOException {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"showOthers\": ").append(showOthers).append(",\n");
        sb.append("  \"showOwn\": ").append(showOwn).append(",\n");
        sb.append("  \"showOwnFirstPerson\": ").append(showOwnFirstPerson).append(",\n");
        sb.append("  \"sendChangeSignal\": ").append(sendChangeSignal).append(",\n");
        sb.append("  \"receiveChangeSignal\": ").append(receiveChangeSignal).append(",\n");
        sb.append("  \"equipped\": [");
        int written = 0;
        for (String id : equipped) {
            if (!ID.matcher(id).matches() || written == 8) continue; // ids are [a-z0-9_]: no escaping needed
            sb.append(written++ == 0 ? "" : ", ").append('"').append(id).append('"');
        }
        sb.append("]\n}\n");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, sb.toString().getBytes(StandardCharsets.US_ASCII));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
