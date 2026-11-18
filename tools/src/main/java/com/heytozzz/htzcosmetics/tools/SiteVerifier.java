package com.heytozzz.htzcosmetics.tools;

import com.heytozzz.htzcosmetics.core.AssetCheck;
import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.crypto.SignatureVerifier;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.model.ModelParser;
import com.heytozzz.htzcosmetics.core.manifest.Manifest;
import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks a built site/ folder exactly the way the mod will: signature, expiry, hashes, model and PNG rules. */
public final class SiteVerifier {
    private SiteVerifier() {
    }

    /** @return problems found (empty = everything OK) */
    public static List<String> verify(Path siteDir, List<String> trustedKeys, long nowEpochSeconds) throws IOException {
        List<String> problems = new ArrayList<>();
        Path manifestFile = siteDir.resolve("manifest.json");
        Path sigFile = siteDir.resolve("manifest.json.sig");
        if (!Files.isRegularFile(manifestFile) || !Files.isRegularFile(sigFile)) {
            problems.add("site/manifest.json or site/manifest.json.sig is missing (run htzBuildSite)");
            return problems;
        }
        Manifest manifest;
        try {
            manifest = ManifestParser.verifyAndParse(Files.readAllBytes(manifestFile), Files.readAllBytes(sigFile),
                SignatureVerifier.fromBase64Keys(trustedKeys.toArray(String[]::new)), nowEpochSeconds, 0);
        } catch (ValidationException e) {
            problems.add("manifest rejected: " + e.getMessage());
            return problems;
        }
        if (manifest.skippedEntries() > 0) problems.add(manifest.skippedEntries() + " manifest entries would be skipped");

        for (CosmeticDefinition def : manifest.cosmetics()) {
            try {
                if (!def.hasModel()) continue; // model-less aura
                ModelParser.checkAnchors(AssetCheck.model(read(siteDir, def.model().sha256()), def.model()), def.category());
                AssetCheck.texture(read(siteDir, def.texture().sha256()), def.texture());
            } catch (ValidationException | IOException e) {
                problems.add(def.id() + ": " + e.getMessage());
            }
        }
        return problems;
    }

    private static byte[] read(Path siteDir, String sha256) throws IOException {
        Path file = siteDir.resolve("a").resolve(sha256);
        if (!Files.isRegularFile(file)) throw new IOException("missing asset file a/" + sha256);
        return Files.readAllBytes(file);
    }
}
