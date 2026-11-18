package com.heytozzz.htzcosmetics.studio;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where everything lives. The studio works on the project folder it was started from
 * ({@code -Dhtzstudio.root=...} or the current folder).
 *
 * <p>Private keys: the studio only knows the keys FOLDER and whether the two files exist. It never opens them;
 * signing is always done by the {@code htzBuildSite} tool.
 */
final class Project {
    final Path root;
    final Path cosmeticsDir;
    final Path siteDir;
    final Path keysDir;
    final Path backendDir;
    /**
     * Dry run (for testing the studio itself): "publish" only re-verifies site/, and deploy/rotation are simulated.
     * Enabled with {@code -Dhtzstudio.dryRun=true} or the environment variable {@code HTZ_STUDIO_DRY_RUN=1}.
     */
    final boolean dryRun;

    private Project(Path root) {
        this.root = root;
        this.cosmeticsDir = root.resolve("cosmetics");
        this.siteDir = root.resolve("site");
        String env = System.getenv("HTZ_KEYS_DIR");
        // Same default as the tools (Cli.DEFAULT_KEYS_DIR).
        this.keysDir = env != null && !env.isBlank() ? Path.of(env)
            : Path.of(System.getProperty("user.home"), ".htzcosmetics", "keys");
        this.backendDir = root.resolve("backend");
        this.dryRun = Boolean.getBoolean("htzstudio.dryRun") || "1".equals(System.getenv("HTZ_STUDIO_DRY_RUN"));
    }

    static Project locate() {
        String prop = System.getProperty("htzstudio.root");
        Path start = (prop != null && !prop.isBlank() ? Path.of(prop) : Path.of("")).toAbsolutePath().normalize();
        for (Path p = start; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("settings.gradle.kts")) && Files.isDirectory(p.resolve("tools"))) {
                return new Project(p);
            }
        }
        throw new IllegalStateException("No encuentro la carpeta del proyecto HtzCosmetics desde " + start);
    }

    boolean primaryKeyExists() {
        return Files.isRegularFile(keysDir.resolve("primary.key"));
    }

    boolean backupKeyExists() {
        return Files.isRegularFile(keysDir.resolve("backup.key"));
    }

    Path gradlew() {
        return root.resolve("gradlew.bat");
    }

    Path asset(String relative) {
        return root.resolve("src/main/resources/assets/htzcosmetics").resolve(relative);
    }
}
