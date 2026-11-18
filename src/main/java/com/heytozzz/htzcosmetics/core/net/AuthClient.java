package com.heytozzz.htzcosmetics.core.net;

import com.heytozzz.htzcosmetics.core.ValidationException;
import com.heytozzz.htzcosmetics.core.json.Json;
import com.heytozzz.htzcosmetics.core.json.StrictJsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Logs in to the backend with the player's Mojang-signed certificate (the chat-signing key pair).
 * Only public data (public key, Mojang's signature, our signature over a one-time challenge) is sent:
 * the private key and the Minecraft access token never leave the computer.
 */
public final class AuthClient {
    /** Must match LOGIN_MESSAGE_PREFIX in backend/src/index.js. */
    public static final String LOGIN_PREFIX = "htzcosmetics-login:v1:";
    private static final Pattern CHALLENGE = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,512}\\.[A-Za-z0-9_-]{43}");
    private static final int MAX_RESPONSE = 4096;

    /** The player's certificate as Minecraft provides it (ProfileKeyPair). */
    public record Certificate(PublicKey publicKey, PrivateKey privateKey, long expiresAtMillis, byte[] mojangSignature) {
    }

    public record Session(String token, long expiresAtMillis) {
        public boolean validFor(long nowMs, long marginMs) {
            return nowMs + marginMs < expiresAtMillis;
        }
    }

    private final SecureHttp http;
    private final URI base;

    public AuthClient(SecureHttp http, URI base) {
        this.http = http;
        this.base = base;
    }

    public Session login(UUID player, Certificate cert) throws IOException, ValidationException {
        String uuid = BackendClient.undashed(player);
        byte[] challengeResponse = http.send("POST", base.resolve("v1/auth/challenge"),
            ("{\"uuid\":\"" + uuid + "\"}").getBytes(StandardCharsets.US_ASCII), null, MAX_RESPONSE);
        Map<String, Object> ch = Json.asObject(StrictJsonParser.parse(challengeResponse, MAX_RESPONSE), "challenge");
        String challenge = Json.string(ch, "challenge", 40);
        if (!CHALLENGE.matcher(challenge).matches()) throw new ValidationException("bad challenge");

        Base64.Encoder b64 = Base64.getEncoder();
        String body = "{\"uuid\":\"" + uuid + "\",\"challenge\":\"" + challenge + "\","
            + "\"publicKey\":\"" + b64.encodeToString(cert.publicKey().getEncoded()) + "\","
            + "\"expiresAt\":" + cert.expiresAtMillis() + ","
            + "\"keySignature\":\"" + b64.encodeToString(cert.mojangSignature()) + "\","
            + "\"signature\":\"" + b64.encodeToString(sign(cert.privateKey(), loginMessage(challenge, uuid))) + "\"}";
        byte[] loginResponse = http.send("POST", base.resolve("v1/auth/certificate"),
            body.getBytes(StandardCharsets.US_ASCII), null, MAX_RESPONSE);

        Map<String, Object> r = Json.asObject(StrictJsonParser.parse(loginResponse, MAX_RESPONSE), "login");
        String token = Json.string(r, "token", 600);
        if (!TOKEN.matcher(token).matches() || !uuid.equals(Json.string(r, "uuid", 32))) {
            throw new ValidationException("bad login response");
        }
        long expiresIn = Json.integer(r, "expiresIn", 60, 24 * 3600);
        return new Session(token, System.currentTimeMillis() + expiresIn * 1000);
    }

    public static byte[] loginMessage(String challenge, String undashedUuid) {
        return (LOGIN_PREFIX + challenge + ":" + undashedUuid).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] sign(PrivateKey key, byte[] message) throws IOException {
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(key);
            s.update(message);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IOException("could not sign login challenge");
        }
    }
}
