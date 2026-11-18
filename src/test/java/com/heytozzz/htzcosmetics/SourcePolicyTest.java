package com.heytozzz.htzcosmetics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security guard-rail: fails the build if dangerous APIs appear anywhere in the mod's source code,
 * or if the pure-Java core starts depending on Minecraft or a mod loader.
 */
class SourcePolicyTest {
    /** APIs that could load or run code, or open files with the OS. Banned in the whole mod. */
    private static final List<String> FORBIDDEN_EVERYWHERE = List.of(
        "ObjectInputStream", "readObject(", "ScriptEngine", "Class.forName", "ClassLoader",
        "defineClass", "MethodHandles", "setAccessible", "java.lang.reflect", "Runtime.getRuntime",
        "ProcessBuilder", "System.load", "java.awt.Desktop", "Unsafe", "javax.script");

    /** The core must stay independent of Minecraft and loaders so it can be shared by every port. */
    private static final List<String> FORBIDDEN_IN_CORE = List.of(
        "import net.minecraft", "import com.mojang", "import net.fabricmc",
        "import net.neoforged", "import net.minecraftforge");

    @Test
    void sourceFollowsSecurityPolicy() throws IOException {
        Path mainSrc = Path.of(System.getProperty("htz.mainSrc", "src/main/java"));
        assertTrue(Files.isDirectory(mainSrc), "Source folder not found: " + mainSrc);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mainSrc)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                boolean isCore = mainSrc.relativize(file).toString().replace('\\', '/')
                    .startsWith("com/heytozzz/htzcosmetics/core/");

                for (String banned : FORBIDDEN_EVERYWHERE) {
                    if (code.contains(banned)) violations.add(file + " uses forbidden API: " + banned);
                }
                if (isCore) {
                    for (String banned : FORBIDDEN_IN_CORE) {
                        if (code.contains(banned)) violations.add(file + " (core) must not contain: " + banned);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "Security policy violations:\n" + String.join("\n", violations));
    }
}
