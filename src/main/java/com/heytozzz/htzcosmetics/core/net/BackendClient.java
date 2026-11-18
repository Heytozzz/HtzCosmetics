package com.heytozzz.htzcosmetics.core.net;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/** Talks to the HtzCosmetics backend. All responses are untrusted and strictly validated. */
public final class BackendClient {
    public static final int MAX_BATCH = 100;
    public static final int MAX_EQUIPPED = 10; // one per category (keep equal to the backend)
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    private final SecureHttp http;
    private final URI base;

    public BackendClient(SecureHttp http, URI base) {
        this.http = http;
        this.base = base;
    }

    public static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /**
     * @return cosmetic ids per player; players with nothing equipped are absent
     */
    public Map<UUID, List<String>> fetchEquipped(Collection<UUID> players) throws IOException, ValidationException {
        return fetch(players, false);
    }

    /** Bypasses the backend's 60 s cache for one player (used after a change signal; rate-limited by the backend). */
    public List<String> fetchEquippedFresh(UUID player) throws IOException, ValidationException {
        return fetch(List.of(player), true).getOrDefault(player, List.of());
    }

    /** Saves the caller's own selection (needs a login token). */
    public void putEquipped(String token, List<String> ids) throws IOException {
        if (ids.size() > MAX_EQUIPPED) throw new IllegalArgumentException("too many ids");
        StringBuilder body = new StringBuilder("{\"ids\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (!ID.matcher(ids.get(i)).matches()) throw new IllegalArgumentException("bad id");
            body.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append('"');
        }
        body.append("]}");
        http.send("PUT", base.resolve("v1/me/equipped"), body.toString().getBytes(StandardCharsets.US_ASCII), token, 4096);
    }

    private Map<UUID, List<String>> fetch(Collection<UUID> players, boolean fresh) throws IOException, ValidationException {
        if (players.isEmpty() || players.size() > MAX_BATCH) throw new IllegalArgumentException("1.." + MAX_BATCH + " players");
        // Sorted so identical lookups share the backend's edge cache.
        Map<String, UUID> requested = new TreeMap<>();
        for (UUID u : players) requested.put(undashed(u), u);

        String query = "v1/equipped?u=" + String.join(",", requested.keySet()) + (fresh ? "&fresh=1" : "");
        byte[] body = http.get(base.resolve(query), MAX_RESPONSE_BYTES);
        Map<String, Object> root = Json.asObject(StrictJsonParser.parse(body, MAX_RESPONSE_BYTES), "response");
        Map<String, Object> playersObj = Json.object(root, "players");

        Map<UUID, List<String>> result = new HashMap<>();
        for (Map.Entry<String, Object> e : playersObj.entrySet()) {
            UUID uuid = requested.get(e.getKey());
            if (uuid == null) continue; // never accept data for players we did not ask about
            List<String> ids = new ArrayList<>();
            for (Object v : Json.asArray(e.getValue(), "ids")) {
                String id = Json.asString(v, "id", 32);
                if (ID.matcher(id).matches() && !ids.contains(id) && ids.size() < MAX_EQUIPPED) ids.add(id);
            }
            result.put(uuid, List.copyOf(ids));
        }
        return result;
    }
}
