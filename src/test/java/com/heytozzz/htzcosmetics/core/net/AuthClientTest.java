package com.heytozzz.htzcosmetics.core.net;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthClientTest {
    private static final UUID PLAYER = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String U = "069a79f444e94726a5befca90e38aaf5";
    private static final String CHALLENGE = "ab".repeat(20);
    private static final String TOKEN = "eyJ1IjoieCJ9." + "A".repeat(43);

    private static byte[] json(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void sendsOnlyPublicDataAndASignatureTheBackendCanVerify() throws Exception {
        KeyPair player = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        byte[] mojangSig = new byte[]{1, 2, 3};
        try (TestServer server = new TestServer()) {
            server.routes.put("/v1/auth/challenge", TestServer.Reply.ok(json("{\"challenge\":\"" + CHALLENGE + "\",\"expiresIn\":120}")));
            server.routes.put("/v1/auth/certificate", TestServer.Reply.ok(json(
                "{\"token\":\"" + TOKEN + "\",\"uuid\":\"" + U + "\",\"expiresIn\":1800}")));

            AuthClient.Session session = new AuthClient(server.client(), server.base()).login(PLAYER,
                new AuthClient.Certificate(player.getPublic(), player.getPrivate(), 1_900_000_000_000L, mojangSig));
            assertEquals(TOKEN, session.token());
            assertTrue(session.validFor(System.currentTimeMillis(), 60_000));

            assertEquals("{\"uuid\":\"" + U + "\"}", server.lastRequest.get("/v1/auth/challenge")[1]);
            String body = server.lastRequest.get("/v1/auth/certificate")[1];
            Map<String, Object> sent = Json.asObject(StrictJsonParser.parse(json(body), 8192, 8, 4096, 100, 1000), "body");
            assertEquals(U, sent.get("uuid"));
            assertEquals(CHALLENGE, sent.get("challenge"));
            assertEquals(1.9e12, sent.get("expiresAt"));
            assertArrayEquals(mojangSig, Base64.getDecoder().decode((String) sent.get("keySignature")));
            assertArrayEquals(player.getPublic().getEncoded(), Base64.getDecoder().decode((String) sent.get("publicKey")));
            // The private key is never sent.
            assertFalse(body.contains(Base64.getEncoder().encodeToString(player.getPrivate().getEncoded())));

            // Exactly the message format the backend checks: "htzcosmetics-login:v1:<challenge>:<uuid>".
            Signature v = Signature.getInstance("SHA256withRSA");
            v.initVerify(player.getPublic());
            v.update(json("htzcosmetics-login:v1:" + CHALLENGE + ":" + U));
            assertTrue(v.verify(Base64.getDecoder().decode((String) sent.get("signature"))));
        }
    }

    @Test
    void rejectsSuspiciousBackendAnswers() throws Exception {
        KeyPair player = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        AuthClient.Certificate cert = new AuthClient.Certificate(player.getPublic(), player.getPrivate(), 1L, new byte[1]);
        try (TestServer server = new TestServer()) {
            AuthClient auth = new AuthClient(server.client(), server.base());
            server.routes.put("/v1/auth/challenge", TestServer.Reply.ok(json("{\"challenge\":\"../../etc\"}")));
            assertThrows(ValidationException.class, () -> auth.login(PLAYER, cert));

            server.routes.put("/v1/auth/challenge", TestServer.Reply.ok(json("{\"challenge\":\"" + CHALLENGE + "\"}")));
            server.routes.put("/v1/auth/certificate", TestServer.Reply.ok(json(
                "{\"token\":\"" + TOKEN + "\",\"uuid\":\"" + "f".repeat(32) + "\",\"expiresIn\":1800}")));
            assertThrows(ValidationException.class, () -> auth.login(PLAYER, cert), "token for another player");

            server.routes.put("/v1/auth/certificate", TestServer.Reply.ok(json(
                "{\"token\":\"x\\ny\",\"uuid\":\"" + U + "\",\"expiresIn\":1800}")));
            assertThrows(ValidationException.class, () -> auth.login(PLAYER, cert), "header injection in token");
        }
    }

    @Test
    void backendClientSavesAndFetchesFresh() throws Exception {
        try (TestServer server = new TestServer()) {
            BackendClient backend = new BackendClient(server.client(), server.base());
            server.routes.put("/v1/me/equipped", TestServer.Reply.ok(json("{\"ok\":true}")));
            backend.putEquipped(TOKEN, List.of("top_hat", "wings"));
            String[] req = server.lastRequest.get("/v1/me/equipped");
            assertEquals("PUT", req[0]);
            assertEquals("{\"ids\":[\"top_hat\",\"wings\"]}", req[1]);
            assertEquals("Bearer " + TOKEN, req[2]);
            assertThrows(IllegalArgumentException.class, () -> backend.putEquipped(TOKEN, List.of("\"},{\"x")));

            server.routes.put("/v1/equipped?u=" + U + "&fresh=1", TestServer.Reply.ok(json("{\"players\":{\"" + U + "\":[\"cape\"]}}")));
            assertEquals(List.of("cape"), backend.fetchEquippedFresh(PLAYER));
        }
    }
}
