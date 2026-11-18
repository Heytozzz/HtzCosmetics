package com.heytozzz.htzcosmetics.studio;

import com.heytozzz.htzcosmetics.core.Limits;
import com.heytozzz.htzcosmetics.core.manifest.CosmeticDefinition;
import com.heytozzz.htzcosmetics.core.manifest.Manifest;
import com.heytozzz.htzcosmetics.core.manifest.ManifestParser;
import com.heytozzz.htzcosmetics.core.net.Endpoints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the published manifest (GitHub Pages) and the local {@code site/manifest.json} to show when the
 * signature expires and what a publication would change. Read-only: the signature itself is checked by the tools.
 */
final class ManifestStatus {
    static final URI PUBLISHED = Endpoints.SITE.resolve("manifest.json");
    static final int WARN_DAYS = 30;
    private static final DateTimeFormatter DATE =
        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES")).withZone(ZoneId.systemDefault());

    /** What a publication changes compared with what players currently get. */
    record Diff(List<String> added, List<String> changed, List<String> removed, int unchanged) {
        boolean contentChanged() {
            return !added.isEmpty() || !changed.isEmpty() || !removed.isEmpty();
        }
    }

    private ManifestStatus() {
    }

    /** The manifest currently online, or null if it cannot be downloaded (no internet, not published yet...). */
    static Manifest fetchPublished() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder(PUBLISHED).timeout(Duration.ofSeconds(10))
                .header("Cache-Control", "no-cache").GET().build();
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200 || res.body().length > Limits.MAX_MANIFEST_BYTES) return null;
            return ManifestParser.parseUnverified(res.body());
        } catch (Exception e) {
            return null;
        }
    }

    static Manifest readLocal(Path siteDir) {
        try {
            Path f = siteDir.resolve("manifest.json");
            return Files.isRegularFile(f) ? ManifestParser.parseUnverified(Files.readAllBytes(f)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    static long daysLeft(long expiresAt) {
        return Math.floorDiv(expiresAt - Instant.now().getEpochSecond(), 86_400L);
    }

    static String date(long epochSeconds) {
        return DATE.format(Instant.ofEpochSecond(epochSeconds));
    }

    static Diff diff(Manifest published, Manifest local) {
        List<String> added = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        int same = 0;
        Map<String, CosmeticDefinition> before = published == null ? Map.of() : published.byId();
        Map<String, CosmeticDefinition> after = local == null ? Map.of() : local.byId();
        for (CosmeticDefinition d : after.values()) {
            CosmeticDefinition old = before.get(d.id());
            if (old == null) added.add(label(d));
            else if (!old.equals(d)) changed.add(label(d) + " — " + whatChanged(old, d));
            else same++;
        }
        for (CosmeticDefinition d : before.values()) if (!after.containsKey(d.id())) removed.add(label(d));
        added.sort(null);
        changed.sort(null);
        removed.sort(null);
        return new Diff(added, changed, removed, same);
    }

    private static String label(CosmeticDefinition d) {
        return d.displayName("es_es") + " (" + d.id() + ")";
    }

    private static String whatChanged(CosmeticDefinition a, CosmeticDefinition b) {
        List<String> parts = new ArrayList<>();
        if (!a.names().equals(b.names())) parts.add("nombre");
        if (a.category() != b.category()) parts.add("categoría");
        if (a.rarity() != b.rarity()) parts.add("rareza");
        if (a.anchor() != b.anchor()) parts.add("anclaje");
        if (!a.model().equals(b.model())) parts.add("modelo");
        if (!a.texture().equals(b.texture())) parts.add("textura");
        return String.join(", ", parts);
    }
}
