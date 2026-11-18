package com.heytozzz.htzcosmetics.core.net;

import com.heytozzz.htzcosmetics.core.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureHttpTest {
    private static final SecureHttp PROD = SecureHttp.production("test");

    @ParameterizedTest
    @ValueSource(strings = {
        "http://heytozzz.github.io/HtzCosmetics/manifest.json",           // not https
        "https://evil.example/manifest.json",                             // not whitelisted
        "https://heytozzz.github.io.evil.example/x",                      // look-alike host
        "https://user:pass@heytozzz.github.io/x",                         // user-info
        "https://heytozzz.github.io:8443/x",                              // other port
        "https://heytozzz.github.io/x#frag",
        "file:///C:/Windows/win.ini",
        "ftp://heytozzz.github.io/x",
        "https://127.0.0.1/x"
    })
    void productionBlocksEverythingOutsideTheWhitelist(String url) {
        assertThrows(IOException.class, () -> PROD.checkAllowed(URI.create(url)));
    }

    @Test
    void productionAllowsOnlyOurEndpoints() throws IOException {
        PROD.checkAllowed(Endpoints.SITE.resolve("manifest.json"));
        PROD.checkAllowed(Endpoints.BACKEND.resolve("v1/equipped?u=x"));
        assertEquals(2, Endpoints.ALLOWED_HOSTS.size());
        assertTrue(Endpoints.SITE.getScheme().equals("https") && Endpoints.BACKEND.getScheme().equals("https"));
    }

    @Test
    void downloadsAndEnforcesLimits() throws Exception {
        try (TestServer server = new TestServer()) {
            SecureHttp http = server.client();
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            server.routes.put("/ok", TestServer.Reply.ok(data));
            assertArrayEquals(data, http.get(server.base().resolve("ok"), 100));

            // Redirects are never followed, even to an allowed host.
            server.routes.put("/redirect", new TestServer.Reply(302, new byte[0],
                Map.of("Location", server.base().resolve("ok").toString()), false));
            SecureHttp.HttpStatusException e = assertThrows(SecureHttp.HttpStatusException.class,
                () -> http.get(server.base().resolve("redirect"), 100));
            assertEquals(302, e.status());
            assertEquals(0, server.hits("/ok") - 1, "the redirect target must not be requested");

            server.routes.put("/big", TestServer.Reply.ok(new byte[10_000]));
            assertThrows(IOException.class, () -> http.get(server.base().resolve("big"), 1_000));
            // No Content-Length (chunked): the cap applies while reading.
            server.routes.put("/chunked", new TestServer.Reply(200, new byte[10_000], Map.of(), true));
            assertThrows(IOException.class, () -> http.get(server.base().resolve("chunked"), 1_000));

            assertThrows(SecureHttp.HttpStatusException.class, () -> http.get(server.base().resolve("missing"), 100));
        }
    }

    @Test
    void backendClientAcceptsOnlyValidDataForRequestedPlayers() throws Exception {
        try (TestServer server = new TestServer()) {
            UUID a = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
            UUID b = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
            String ua = BackendClient.undashed(a);
            String ub = BackendClient.undashed(b);
            String query = "/v1/equipped?u=" + (ua.compareTo(ub) < 0 ? ua + "," + ub : ub + "," + ua);
            server.routes.put(query, TestServer.Reply.ok(("{\"players\":{"
                + "\"" + ua + "\":[\"top_hat\",\"../evil\",\"BAD\",\"top_hat\",\"wings\"],"
                + "\"" + "f".repeat(32) + "\":[\"spam\"]}}").getBytes(StandardCharsets.UTF_8)));

            Map<UUID, List<String>> result = new BackendClient(server.client(), server.base()).fetchEquipped(List.of(b, a));
            assertEquals(Map.of(a, List.of("top_hat", "wings")), result);

            server.routes.put(query, TestServer.Reply.ok("{\"players\":[1e999]}".getBytes(StandardCharsets.UTF_8)));
            assertThrows(ValidationException.class,
                () -> new BackendClient(server.client(), server.base()).fetchEquipped(List.of(a, b)));
        }
    }
}
