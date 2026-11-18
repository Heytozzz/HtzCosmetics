package com.heytozzz.htzcosmetics.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and rewrites the key list inside the mod's {@code TrustedKeys.java}. */
final class TrustedKeysFile {
    static final Path DEFAULT = Path.of("src/main/java/com/heytozzz/htzcosmetics/core/crypto/TrustedKeys.java");

    private static final Pattern BLOCK = Pattern.compile(
        "(// BEGIN TRUSTED KEYS[^\\n]*\\n)(.*?)(\\s*// END TRUSTED KEYS)", Pattern.DOTALL);
    private static final Pattern KEY = Pattern.compile("\"([A-Za-z0-9+/=]{44})\"");

    private TrustedKeysFile() {
    }

    static List<String> read(Path file) throws IOException {
        Matcher m = block(file);
        List<String> keys = new ArrayList<>();
        Matcher k = KEY.matcher(m.group(2));
        while (k.find()) keys.add(k.group(1));
        return keys;
    }

    static void write(Path file, List<String> keys) throws IOException {
        Matcher m = block(file);
        StringBuilder body = new StringBuilder("    private static final String[] KEYS = {\n");
        for (int i = 0; i < keys.size(); i++) {
            String role = i == 0 ? "primary" : "backup";
            body.append("        \"").append(keys.get(i)).append("\", // ").append(role).append('\n');
        }
        body.append("    };");
        String code = Files.readString(file, StandardCharsets.UTF_8);
        String updated = code.substring(0, m.start(2)) + body + code.substring(m.end(2));
        Files.writeString(file, updated, StandardCharsets.UTF_8);
    }

    private static Matcher block(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new ToolException("Cannot find " + file + " (run from the project folder)");
        Matcher m = BLOCK.matcher(Files.readString(file, StandardCharsets.UTF_8));
        if (!m.find()) throw new ToolException("BEGIN/END TRUSTED KEYS markers not found in " + file);
        return m;
    }
}
