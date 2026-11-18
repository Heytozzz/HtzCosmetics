package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.crypto.TrustedKeys;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * {@code gradlew htzBuildSite}: cosmetics/ → signed site/. Options (via {@code --args="..."}):
 * {@code --days N} (manifest validity, default 180), {@code --keys <folder>}, {@code --in <dir>}, {@code --out <dir>}.
 */
public final class BuildSiteCommand {
    private BuildSiteCommand() {
    }

    public static void main(String[] args) {
        Cli.run("htzBuildSite", () -> {
            Cli cli = new Cli(args);
            Path keysDir = cli.keysDir();
            Path in = Path.of(cli.option("--in", "cosmetics"));
            Path out = Path.of(cli.option("--out", "site"));
            int days = Integer.parseInt(cli.option("--days", "180"));
            cli.requireNoMoreArgs();

            List<String> trusted = TrustedKeysFile.read(TrustedKeysFile.DEFAULT);
            if (trusted.isEmpty() || !trusted.equals(TrustedKeys.base64Keys())) {
                throw new ToolException("TrustedKeys.java has no keys yet or changed. Run 'gradlew htzKeygen' first.");
            }
            long now = Instant.now().getEpochSecond();
            SiteBuilder.Summary s = SiteBuilder.build(new SiteBuilder.Options(
                in, out, SigningKey.load(keysDir.resolve("primary.key")), trusted, now, days));

            for (String w : s.warnings()) System.out.println("  warning: " + w);
            List<String> problems = SiteVerifier.verify(out, trusted, now);
            if (!problems.isEmpty()) throw new ToolException("Verification failed:\n  " + String.join("\n  ", problems));

            System.out.println();
            System.out.println("Site built and verified: " + s.cosmetics() + " cosmetic(s), manifest version "
                + s.manifestVersion() + ", valid until " + Instant.ofEpochSecond(s.expiresAt()).toString().substring(0, 10) + ".");
            System.out.println("Upload the contents of " + out.toAbsolutePath() + " to GitHub Pages.");
            System.out.println("Re-run this command before that date (or whenever you change cosmetics).");
        });
    }
}
