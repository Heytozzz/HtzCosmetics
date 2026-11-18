package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code gradlew htzPublishSite}: verifies site/ and pushes it to the {@code gh-pages} branch of the project's
 * GitHub repository (served by GitHub Pages). Only public, signed files are pushed; keys never live in site/.
 *
 * <p>site/ is its own small git repository (ignored by the main one), so publishing never touches your code.
 */
public final class PublishSiteCommand {
    private static final String BRANCH = "gh-pages";

    private PublishSiteCommand() {
    }

    public static void main(String[] args) {
        Cli.run("htzPublishSite", () -> {
            Cli cli = new Cli(args);
            Path site = Path.of(cli.option("--in", "site")).toAbsolutePath();
            cli.requireNoMoreArgs();

            List<String> trusted = TrustedKeysFile.read(TrustedKeysFile.DEFAULT);
            List<String> problems = SiteVerifier.verify(site, trusted, Instant.now().getEpochSecond());
            if (!problems.isEmpty()) {
                throw new ToolException("site/ is not valid, nothing was published:\n  " + String.join("\n  ", problems)
                    + "\nRun 'gradlew htzBuildSite' first.");
            }
            long version = ManifestParser.parseUnverified(Files.readAllBytes(site.resolve("manifest.json"))).manifestVersion();

            String remote = git(Path.of("").toAbsolutePath(), "remote", "get-url", "origin").trim();
            if (!Files.isDirectory(site.resolve(".git"))) {
                git(site, "init", "-q", "-b", BRANCH);
                git(site, "remote", "add", "origin", remote);
                // Keep the published history if the branch already exists (e.g. on a new computer).
                if (tryGit(site, "fetch", "-q", "origin", BRANCH)) git(site, "reset", "-q", "--soft", "FETCH_HEAD");
            }
            git(site, "add", "-A");
            if (tryGit(site, "diff", "--cached", "--quiet")) {
                System.out.println("Nothing new to publish: GitHub already has manifest version " + version + ".");
                return;
            }
            git(site, "commit", "-q", "-m", "Publish cosmetics manifest version " + version);
            git(site, "push", "-q", "origin", BRANCH);
            System.out.println("Published manifest version " + version + " to the " + BRANCH + " branch.");
            System.out.println("GitHub Pages usually updates within 1-2 minutes.");
        });
    }

    private static String git(Path dir, String... args) throws IOException, InterruptedException {
        Result r = run(dir, args);
        if (r.exit != 0) throw new ToolException("git " + String.join(" ", args) + " failed:\n" + r.output);
        return r.output;
    }

    private static boolean tryGit(Path dir, String... args) throws IOException, InterruptedException {
        return run(dir, args).exit == 0;
    }

    private record Result(int exit, String output) {
    }

    private static Result run(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(p.waitFor(), out);
    }
}
