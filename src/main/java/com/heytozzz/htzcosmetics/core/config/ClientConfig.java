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

/**
 * Player options, stored in {@code config/htzcosmetics.json}. A damaged or hand-edited file never breaks the game:
 * unknown keys are ignored and anything invalid falls back to the default.
 */
public final class ClientConfig {
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
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (Object v : Json.array(root, "equipped", com.heytozzz.htzcosmetics.core.net.BackendClient.MAX_EQUIPPED)) {
                    var entry = v instanceof String text ? com.heytozzz.htzcosmetics.core.net.EquippedEntry.parse(text) : null;
                    if (entry != null && seen.add(entry.id())) config.equipped.add(entry.format());
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
            // "id" or "id:rrggbb" ([a-z0-9_:] only): no escaping needed
            if (com.heytozzz.htzcosmetics.core.net.EquippedEntry.parse(id) == null
                || written == com.heytozzz.htzcosmetics.core.net.BackendClient.MAX_EQUIPPED) continue;
            sb.append(written++ == 0 ? "" : ", ").append('"').append(id).append('"');
        }
        sb.append("]\n}\n");
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, sb.toString().getBytes(StandardCharsets.US_ASCII));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
