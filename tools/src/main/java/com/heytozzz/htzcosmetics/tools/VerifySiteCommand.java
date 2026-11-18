package com.heytozzz.htzcosmetics.tools;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** {@code gradlew htzVerifySite}: re-checks site/ with the mod's own rules ({@code --args="--in <dir>"}). */
public final class VerifySiteCommand {
    private VerifySiteCommand() {
    }

    public static void main(String[] args) {
        Cli.run("htzVerifySite", () -> {
            Cli cli = new Cli(args);
            Path site = Path.of(cli.option("--in", "site"));
            cli.requireNoMoreArgs();
            List<String> trusted = TrustedKeysFile.read(TrustedKeysFile.DEFAULT);
            if (trusted.isEmpty()) throw new ToolException("No trusted keys yet. Run 'gradlew htzKeygen' first.");
            List<String> problems = SiteVerifier.verify(site, trusted, Instant.now().getEpochSecond());
            if (!problems.isEmpty()) throw new ToolException("Problems found:\n  " + String.join("\n  ", problems));
            System.out.println("OK: " + site.toAbsolutePath() + " passes every check the mod performs.");
        });
    }
}
