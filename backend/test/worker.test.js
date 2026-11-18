import { env as baseEnv } from "cloudflare:workers";
import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import worker, { certificatePayload, LOGIN_MESSAGE_PREFIX, signToken } from "../src/index.js";
import { MOJANG_PLAYER_CERTIFICATE_KEYS } from "../src/mojang-keys.js";

const UUID = "0123456789abcdef0123456789abcdef";
const OTHER = "fedcba9876543210fedcba9876543210";
let ipCounter = 0;
let env;
let fakeMojang;
let player;

const b64 = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes)));
const rsa = (hash) => crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash }, true,
  ["sign", "verify"]);
const sign = async (key, data) => new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, data));

beforeAll(async () => {
  // A stand-in for Mojang's certificate key (SHA1withRSA) and a player's chat key pair (SHA256withRSA).
  fakeMojang = await rsa("SHA-1");
  player = await rsa("SHA-256");
  const mojangSpki = b64(await crypto.subtle.exportKey("spki", fakeMojang.publicKey));
  env = { ...baseEnv, MOJANG_KEYS_FOR_TESTS: [mojangSpki] };
});

async function call(method, path, { body, token, raw, ip, scheme = "https" } = {}) {
  // A different client IP per call unless a test sets one, so rate limits only apply where tested.
  const headers = { "CF-Connecting-IP": ip || `198.51.${(ipCounter >> 8) & 255}.${ipCounter++ & 255}` };
  if (token) headers.Authorization = `Bearer ${token}`;
  const init = { method, headers };
  if (raw !== undefined) init.body = raw;
  else if (body !== undefined) {
    init.body = JSON.stringify(body);
    headers["Content-Type"] = "application/json";
  }
  const ctx = createExecutionContext();
  const res = await worker.fetch(new Request(`${scheme}://api.test${path}`, init), env, ctx);
  await waitOnExecutionContext(ctx);
  return { status: res.status, json: await res.json() };
}

/** Builds what the mod sends: Mojang-signed certificate + signature over the challenge. */
async function certificateBody(challenge, { uuid = UUID, expiresAt = Date.now() + 3600_000, signer = player,
  certUuid = uuid, mojang = fakeMojang } = {}) {
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", signer.publicKey));
  const keySignature = await sign(mojang.privateKey, certificatePayload(certUuid, expiresAt, spki));
  const signature = await sign(signer.privateKey, new TextEncoder().encode(LOGIN_MESSAGE_PREFIX + challenge + ":" + uuid));
  return { uuid, challenge, publicKey: b64(spki), expiresAt, keySignature: b64(keySignature), signature: b64(signature) };
}

async function newChallenge(uuid = UUID) {
  const r = await call("POST", "/v1/auth/challenge", { body: { uuid } });
  expect(r.status).toBe(200);
  return r.json.challenge;
}

async function login(uuid = UUID) {
  const r = await call("POST", "/v1/auth/certificate", { body: await certificateBody(await newChallenge(uuid), { uuid }) });
  expect(r.status).toBe(200);
  return r.json.token;
}

beforeEach(async () => {
  await baseEnv.DB.exec("DELETE FROM equipped; DELETE FROM challenges;");
});

describe("auth", () => {
  it("issues a token for a valid Mojang-signed certificate and challenge signature", async () => {
    const challenge = await newChallenge();
    expect(challenge).toMatch(/^[0-9a-f]{40}$/);
    const r = await call("POST", "/v1/auth/certificate", { body: await certificateBody(challenge) });
    expect(r.status).toBe(200);
    expect(r.json.uuid).toBe(UUID);
  });

  it("a challenge works only once", async () => {
    const body = await certificateBody(await newChallenge());
    expect((await call("POST", "/v1/auth/certificate", { body })).status).toBe(200);
    expect((await call("POST", "/v1/auth/certificate", { body })).json.error).toBe("invalid_challenge");
  });

  it("rejects certificates not signed by Mojang, for another UUID, or expired", async () => {
    const impostor = await rsa("SHA-1");
    let r = await call("POST", "/v1/auth/certificate", { body: await certificateBody(await newChallenge(), { mojang: impostor }) });
    expect(r.json.error).toBe("invalid_certificate");
    // Someone reuses another player's real certificate to claim that player's UUID.
    r = await call("POST", "/v1/auth/certificate", { body: await certificateBody(await newChallenge(), { certUuid: OTHER }) });
    expect(r.json.error).toBe("invalid_certificate");
    r = await call("POST", "/v1/auth/certificate", { body: await certificateBody(await newChallenge(), { expiresAt: Date.now() - 1 }) });
    expect(r.json.error).toBe("certificate_expired");
  });

  it("rejects a stolen certificate used without its private key", async () => {
    const thief = await rsa("SHA-256");
    const challenge = await newChallenge();
    const honest = await certificateBody(challenge);
    const forged = await certificateBody(challenge, { signer: thief });
    const r = await call("POST", "/v1/auth/certificate", { body: { ...honest, signature: forged.signature } });
    expect(r.json.error).toBe("invalid_signature");
  });

  it("rejects a challenge issued for another UUID or expired", async () => {
    let r = await call("POST", "/v1/auth/certificate", { body: await certificateBody(await newChallenge(OTHER)) });
    expect(r.json.error).toBe("invalid_challenge");
    await baseEnv.DB.prepare("INSERT INTO challenges VALUES (?, ?, ?)").bind("b".repeat(40), UUID, 1000).run();
    r = await call("POST", "/v1/auth/certificate", { body: await certificateBody("b".repeat(40)) });
    expect(r.json.error).toBe("invalid_challenge");
  });

  it("rejects malformed input, including injection attempts and huge bodies", async () => {
    for (const uuid of ["", "x' OR 1=1 --", "../etc", UUID + "0", UUID.toUpperCase(), 5]) {
      expect((await call("POST", "/v1/auth/challenge", { body: { uuid } })).status).toBe(400);
    }
    expect((await call("POST", "/v1/auth/challenge", { raw: "not json" })).status).toBe(400);
    expect((await call("POST", "/v1/auth/challenge", { raw: "[]" })).status).toBe(400);
    expect((await call("POST", "/v1/auth/challenge", { raw: "x".repeat(5000) })).status).toBe(413);
    const good = await certificateBody(await newChallenge());
    for (const patch of [{ publicKey: "%%%" }, { signature: "A".repeat(1500) }, { expiresAt: "soon" }, { expiresAt: 1.5 }]) {
      expect((await call("POST", "/v1/auth/certificate", { body: { ...good, ...patch } })).status).toBe(400);
    }
  });

  it("embeds Mojang's real certificate keys in a format WebCrypto accepts", async () => {
    expect(MOJANG_PLAYER_CERTIFICATE_KEYS.length).toBeGreaterThan(0);
    for (const k of MOJANG_PLAYER_CERTIFICATE_KEYS) {
      const der = Uint8Array.from(atob(k), (c) => c.charCodeAt(0));
      await crypto.subtle.importKey("spki", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]);
    }
  });
});

describe("equipped", () => {
  it("saves and returns a player's cosmetics in batch", async () => {
    const token = await login();
    expect((await call("PUT", "/v1/me/equipped", { token, body: { ids: ["top_hat", "wings"] } })).status).toBe(200);
    const r = await call("GET", `/v1/equipped?u=${OTHER},${UUID.toUpperCase()}`);
    expect(r.status).toBe(200);
    expect(r.json.players).toEqual({ [UUID]: ["top_hat", "wings"] });
  });

  it("requires a valid, unexpired, untampered token", async () => {
    const body = { ids: ["top_hat"] };
    expect((await call("PUT", "/v1/me/equipped", { body })).status).toBe(401);
    const token = await login();
    const [payload, sig] = token.split(".");
    const forged = btoa(JSON.stringify({ u: OTHER, exp: 9999999999 })).replace(/=+$/, "") + "." + sig;
    expect((await call("PUT", "/v1/me/equipped", { token: forged, body })).status).toBe(401);
    expect((await call("PUT", "/v1/me/equipped", { token: payload + "." + "A".repeat(43), body })).status).toBe(401);
    const expired = await signToken(env, { u: UUID, exp: 1000 });
    expect((await call("PUT", "/v1/me/equipped", { token: expired, body })).json.error).toBe("token_expired");
    const otherSecret = await signToken({ TOKEN_SECRET: "x".repeat(40) }, { u: UUID, exp: 9999999999 });
    expect((await call("PUT", "/v1/me/equipped", { token: otherSecret, body })).status).toBe(401);
  });

  it("validates cosmetic ids", async () => {
    const token = await login();
    for (const ids of [["../x"], ["UPPER"], ["a".repeat(33)], [1], "top_hat", Array(11).fill("a")]) {
      expect((await call("PUT", "/v1/me/equipped", { token, body: { ids } })).status).toBe(400);
    }
  });

  it("throttles rapid writes and stores nothing for an empty list", async () => {
    const token = await login();
    expect((await call("PUT", "/v1/me/equipped", { token, body: { ids: ["a"] } })).status).toBe(200);
    expect((await call("PUT", "/v1/me/equipped", { token, body: { ids: ["b"] } })).status).toBe(429);
    await baseEnv.DB.prepare("UPDATE equipped SET updated_at = 0").run();
    expect((await call("PUT", "/v1/me/equipped", { token, body: { ids: [] } })).status).toBe(200);
    expect(await baseEnv.DB.prepare("SELECT COUNT(*) AS n FROM equipped").first("n")).toBe(0);
  });

  it("lets a player delete all their data", async () => {
    const token = await login();
    await call("PUT", "/v1/me/equipped", { token, body: { ids: ["a"] } });
    expect((await call("DELETE", "/v1/me", { token })).status).toBe(200);
    expect(await baseEnv.DB.prepare("SELECT COUNT(*) AS n FROM equipped").first("n")).toBe(0);
  });

  it("fresh lookups skip the cache, for one player only, and are rate-limited", async () => {
    const P = "11112222333344445555666677778888"; // own player: earlier tests used up UUID's write limit
    const token = await login(P);
    await call("PUT", "/v1/me/equipped", { token, body: { ids: ["a"] } });
    expect((await call("GET", `/v1/equipped?u=${P}`)).json.players).toEqual({ [P]: ["a"] }); // now cached
    await baseEnv.DB.prepare("UPDATE equipped SET updated_at = 0").run();
    await call("PUT", "/v1/me/equipped", { token, body: { ids: ["b"] } });
    expect((await call("GET", `/v1/equipped?u=${P}`)).json.players).toEqual({ [P]: ["a"] }); // stale cache
    expect((await call("GET", `/v1/equipped?u=${P}&fresh=1`)).json.players).toEqual({ [P]: ["b"] });
    expect((await call("GET", `/v1/equipped?u=${P}`)).json.players).toEqual({ [P]: ["b"] }); // cache refreshed
    expect((await call("GET", `/v1/equipped?u=${P},${OTHER}&fresh=1`)).status).toBe(400);
    const statuses = [];
    for (let i = 0; i < 32; i++) statuses.push((await call("GET", `/v1/equipped?u=${OTHER}&fresh=1`, { ip: "192.0.2.50" })).status);
    expect(statuses.at(-1)).toBe(429);
  });

  it("rejects bad player lists", async () => {
    expect((await call("GET", "/v1/equipped")).status).toBe(400);
    expect((await call("GET", "/v1/equipped?u=not-a-uuid")).status).toBe(400);
    const many = Array.from({ length: 101 }, (_, i) => i.toString(16).padStart(32, "0")).join(",");
    expect((await call("GET", `/v1/equipped?u=${many}`)).status).toBe(400);
  });
});

describe("misc", () => {
  it("rate-limits login attempts per IP", async () => {
    const statuses = [];
    for (let i = 0; i < 12; i++) {
      statuses.push((await call("POST", "/v1/auth/challenge", { body: { uuid: UUID }, ip: "192.0.2.99" })).status);
    }
    expect(statuses.slice(0, 10).every((s) => s === 200)).toBe(true);
    expect(statuses.at(-1)).toBe(429);
  });

  it("refuses plain http", async () => {
    expect((await call("GET", "/", { scheme: "http" })).status).toBe(403);
  });

  it("health and unknown routes", async () => {
    expect((await call("GET", "/")).json.ok).toBe(true);
    expect((await call("GET", "/admin")).status).toBe(404);
    expect((await call("POST", "/v1/auth/verify")).status).toBe(404);
  });

  it("cron removes only expired challenges", async () => {
    await baseEnv.DB.prepare("INSERT INTO challenges VALUES (?, ?, ?), (?, ?, ?)")
      .bind("c".repeat(40), "old", 1000, "d".repeat(40), "new", Math.floor(Date.now() / 1000)).run();
    await worker.scheduled({}, env);
    const { results } = await baseEnv.DB.prepare("SELECT name FROM challenges").all();
    expect(results.map((r) => r.name)).toEqual(["new"]);
  });
});
