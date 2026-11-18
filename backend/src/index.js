/**
 * HtzCosmetics backend (Cloudflare Worker + D1).
 *
 * Stores ONLY: verified Minecraft UUID -> list of equipped cosmetic ids (+ last update time).
 * No IPs, names, emails or Minecraft tokens are ever stored, and the Minecraft access token never reaches this server.
 *
 * Identity: every Minecraft account has a Mojang-signed "player certificate" (the RSA key pair used for chat signing).
 * The client signs our one-time challenge with its private key (which never leaves the PC); we check that Mojang
 * signed the certificate for that UUID and that the challenge signature matches. No call to Mojang is needed
 * (Mojang's session server refuses requests coming from Cloudflare Workers).
 *
 *   POST   /v1/auth/challenge    {"uuid"}                                       -> {"challenge","expiresIn"}
 *   POST   /v1/auth/certificate  {"uuid","challenge","publicKey","expiresAt",
 *                                 "keySignature","signature"}                   -> {"token","uuid","expiresIn"}
 *   GET    /v1/equipped?u=<uuid>,<uuid>,...[&fresh=1 with one uuid]             -> {"players":{"<uuid>":["id",...]}}
 *   PUT    /v1/me/equipped       {"ids":[...]}                           Bearer -> {"ok":true}
 *   DELETE /v1/me                                                        Bearer -> {"ok":true}  (deletes the caller's data)
 */

import { MOJANG_PLAYER_CERTIFICATE_KEYS } from "./mojang-keys.js";

const UUID_RE = /^[0-9a-f]{32}$/;
const ID_RE = /^[a-z0-9_]{1,32}$/;
const CHALLENGE_RE = /^[0-9a-f]{40}$/;
const B64_RE = /^[A-Za-z0-9+/]+={0,2}$/;

export const CHALLENGE_TTL_SECONDS = 120;
export const TOKEN_TTL_SECONDS = 30 * 60;
/** Text the client signs; the prefix keeps it from ever looking like a signed chat message. */
export const LOGIN_MESSAGE_PREFIX = "htzcosmetics-login:v1:";
const MAX_BATCH = 100;
const MAX_EQUIPPED = 8;
const MAX_BODY_BYTES = 4096;
const MIN_SECONDS_BETWEEN_WRITES = 2;
const EQUIPPED_CACHE_SECONDS = 60;
const MAX_CERT_LIFETIME_MS = 30 * 24 * 3600 * 1000;

class HttpError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

export default {
  async fetch(request, env, ctx) {
    try {
      return await route(request, env, ctx);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.code }, e.status);
      console.error("unexpected error", e && e.name); // never log request data
      return json({ error: "internal" }, 500);
    }
  },

  /** Cron: removes expired, unused login challenges. */
  async scheduled(controller, env) {
    await env.DB.prepare("DELETE FROM challenges WHERE created_at < ?").bind(now() - CHALLENGE_TTL_SECONDS).run();
  },
};

async function route(request, env, ctx) {
  const url = new URL(request.url);
  const { pathname } = url;
  const method = request.method;

  // Tokens must never travel unencrypted; the mod only uses https, this also refuses plain-http callers.
  if (url.protocol !== "https:") throw new HttpError(403, "https_required");
  if (pathname === "/" && method === "GET") return json({ service: "htzcosmetics", ok: true });
  if (pathname === "/v1/auth/challenge" && method === "POST") return challenge(request, env);
  if (pathname === "/v1/auth/certificate" && method === "POST") return certificateLogin(request, env);
  if (pathname === "/v1/equipped" && method === "GET") return getEquipped(request, url, env, ctx);
  if (pathname === "/v1/me/equipped" && method === "PUT") return putEquipped(request, env);
  if (pathname === "/v1/me" && method === "DELETE") return deleteMe(request, env);
  throw new HttpError(404, "not_found");
}

// ---------------------------------------------------------------- auth

async function challenge(request, env) {
  await limit(env.AUTH_LIMITER, "auth:" + clientIp(request));
  const body = await readJson(request);
  const uuid = requireString(body.uuid, UUID_RE);
  const value = hex(crypto.getRandomValues(new Uint8Array(20)));
  await env.DB.prepare("INSERT INTO challenges (server_id, name, created_at) VALUES (?, ?, ?)")
    .bind(value, uuid, now()).run();
  return json({ challenge: value, expiresIn: CHALLENGE_TTL_SECONDS });
}

async function certificateLogin(request, env) {
  await limit(env.AUTH_LIMITER, "auth:" + clientIp(request));
  const body = await readJson(request);
  const uuid = requireString(body.uuid, UUID_RE);
  const challengeValue = requireString(body.challenge, CHALLENGE_RE);
  const publicKey = decodeB64(body.publicKey, 1024);
  const keySignature = decodeB64(body.keySignature, 1024);
  const signature = decodeB64(body.signature, 1024);
  const expiresAt = body.expiresAt;
  if (!Number.isSafeInteger(expiresAt)) throw new HttpError(400, "bad_request");

  // Single use: the challenge is deleted in the same statement that checks it.
  const row = await env.DB.prepare(
    "DELETE FROM challenges WHERE server_id = ? AND name = ? AND created_at >= ? RETURNING server_id")
    .bind(challengeValue, uuid, now() - CHALLENGE_TTL_SECONDS).first();
  if (!row) throw new HttpError(401, "invalid_challenge");

  const nowMs = Date.now();
  if (expiresAt <= nowMs || expiresAt > nowMs + MAX_CERT_LIFETIME_MS) throw new HttpError(401, "certificate_expired");

  // 1) Mojang vouches that this public key belongs to this UUID (the same check Minecraft servers do).
  const mojangKeys = env.MOJANG_KEYS_FOR_TESTS || MOJANG_PLAYER_CERTIFICATE_KEYS;
  const payload = certificatePayload(uuid, expiresAt, publicKey);
  let vouched = false;
  for (const keyB64 of mojangKeys) {
    if (await rsaVerify(b64Bytes(keyB64), "SHA-1", keySignature, payload)) {
      vouched = true;
      break;
    }
  }
  if (!vouched) throw new HttpError(401, "invalid_certificate");

  // 2) The caller holds the matching private key: it signed our fresh challenge.
  const message = new TextEncoder().encode(LOGIN_MESSAGE_PREFIX + challengeValue + ":" + uuid);
  if (!(await rsaVerify(publicKey, "SHA-256", signature, message))) throw new HttpError(401, "invalid_signature");

  const token = await signToken(env, { u: uuid, exp: now() + TOKEN_TTL_SECONDS });
  return json({ token, uuid, expiresIn: TOKEN_TTL_SECONDS });
}

/** Exactly what Mojang signs: UUID (16 bytes) + expiry millis (8 bytes, big-endian) + X.509 public key. */
export function certificatePayload(uuid, expiresAtMs, publicKeyDer) {
  const out = new Uint8Array(24 + publicKeyDer.length);
  for (let i = 0; i < 16; i++) out[i] = parseInt(uuid.substr(i * 2, 2), 16);
  new DataView(out.buffer).setBigInt64(16, BigInt(expiresAtMs), false);
  out.set(publicKeyDer, 24);
  return out;
}

async function rsaVerify(spkiDer, hash, signature, data) {
  try {
    const key = await crypto.subtle.importKey("spki", spkiDer, { name: "RSASSA-PKCS1-v1_5", hash }, false, ["verify"]);
    return await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, data);
  } catch {
    return false; // malformed key or signature
  }
}

function b64Bytes(b64) {
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

function decodeB64(value, maxBytes) {
  if (typeof value !== "string" || value.length > Math.ceil(maxBytes / 3) * 4 || !B64_RE.test(value)) {
    throw new HttpError(400, "bad_request");
  }
  try {
    return b64Bytes(value);
  } catch {
    throw new HttpError(400, "bad_request");
  }
}

// ---------------------------------------------------------------- equipped

async function getEquipped(request, url, env, ctx) {
  await limit(env.READ_LIMITER, "read:" + clientIp(request));
  const raw = url.searchParams.get("u") || "";
  if (raw.length > MAX_BATCH * 33) throw new HttpError(400, "too_many_players");
  const uuids = [...new Set(raw.split(",").filter((s) => s.length > 0).map((s) => s.toLowerCase()))].sort();
  if (uuids.length === 0 || uuids.length > MAX_BATCH) throw new HttpError(400, "bad_players");
  for (const u of uuids) if (!UUID_RE.test(u)) throw new HttpError(400, "bad_players");

  // "fresh=1": skip the cache for ONE player, after that player signalled a change. Separately rate-limited.
  const fresh = url.searchParams.get("fresh") === "1";
  if (fresh) {
    if (uuids.length !== 1) throw new HttpError(400, "fresh_needs_one_player");
    await limit(env.FRESH_LIMITER, "fresh:" + clientIp(request));
  }

  // Edge cache keyed by the canonical (sorted) list: repeated lookups do not touch the database.
  const cacheKey = new Request(`${url.origin}/v1/equipped?u=${uuids.join(",")}`, { method: "GET" });
  const cache = caches.default;
  if (!fresh) {
    const cached = await cache.match(cacheKey);
    if (cached) return cached;
  }

  const placeholders = uuids.map(() => "?").join(",");
  const { results } = await env.DB.prepare(`SELECT uuid, ids FROM equipped WHERE uuid IN (${placeholders})`)
    .bind(...uuids).all();
  const players = {};
  for (const r of results) players[r.uuid] = JSON.parse(r.ids);

  const response = json({ players }, 200, { "Cache-Control": `public, max-age=${EQUIPPED_CACHE_SECONDS}` });
  ctx.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

async function putEquipped(request, env) {
  const uuid = await authenticate(request, env);
  await limit(env.WRITE_LIMITER, "write:" + uuid);
  const body = await readJson(request);
  if (!Array.isArray(body.ids) || body.ids.length > MAX_EQUIPPED) throw new HttpError(400, "bad_ids");
  for (const id of body.ids) requireString(id, ID_RE);
  const ids = [...new Set(body.ids)];

  if (ids.length === 0) {
    // Nothing equipped: keep no data at all about this player.
    await env.DB.prepare("DELETE FROM equipped WHERE uuid = ?").bind(uuid).run();
    return json({ ok: true });
  }
  const t = now();
  const result = await env.DB.prepare(
    "INSERT INTO equipped (uuid, ids, updated_at) VALUES (?, ?, ?) " +
    "ON CONFLICT(uuid) DO UPDATE SET ids = excluded.ids, updated_at = excluded.updated_at " +
    "WHERE equipped.updated_at <= ?")
    .bind(uuid, JSON.stringify(ids), t, t - MIN_SECONDS_BETWEEN_WRITES).run();
  if (result.meta.changes === 0) throw new HttpError(429, "too_fast");
  return json({ ok: true });
}

async function deleteMe(request, env) {
  const uuid = await authenticate(request, env);
  await env.DB.prepare("DELETE FROM equipped WHERE uuid = ?").bind(uuid).run();
  return json({ ok: true });
}

// ---------------------------------------------------------------- tokens (HMAC-SHA256, stateless)

async function hmacKey(env) {
  const secret = env.TOKEN_SECRET;
  if (typeof secret !== "string" || secret.length < 32) throw new Error("TOKEN_SECRET missing or too short");
  return crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" },
    false, ["sign", "verify"]);
}

export async function signToken(env, payload) {
  const body = b64url(new TextEncoder().encode(JSON.stringify(payload)));
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(env), new TextEncoder().encode(body));
  return `${body}.${b64url(new Uint8Array(sig))}`;
}

async function authenticate(request, env) {
  const header = request.headers.get("Authorization") || "";
  const match = /^Bearer ([A-Za-z0-9_-]{1,512})\.([A-Za-z0-9_-]{43})$/.exec(header);
  if (!match) throw new HttpError(401, "unauthorized");
  let sig;
  try {
    sig = fromB64url(match[2]);
  } catch {
    throw new HttpError(401, "unauthorized");
  }
  // crypto.subtle.verify compares in constant time.
  const ok = await crypto.subtle.verify("HMAC", await hmacKey(env), sig, new TextEncoder().encode(match[1]));
  if (!ok) throw new HttpError(401, "unauthorized");
  let payload;
  try {
    payload = JSON.parse(new TextDecoder().decode(fromB64url(match[1])));
  } catch {
    throw new HttpError(401, "unauthorized");
  }
  if (typeof payload.u !== "string" || !UUID_RE.test(payload.u)) throw new HttpError(401, "unauthorized");
  if (typeof payload.exp !== "number" || payload.exp <= now()) throw new HttpError(401, "token_expired");
  return payload.u;
}

// ---------------------------------------------------------------- helpers

async function limit(limiter, key) {
  if (!limiter) return; // binding not configured (e.g. local tests)
  const { success } = await limiter.limit({ key });
  if (!success) throw new HttpError(429, "rate_limited");
}

function clientIp(request) {
  // Used only as an in-memory rate-limit key; never stored.
  return request.headers.get("CF-Connecting-IP") || "unknown";
}

async function readJson(request) {
  const declared = Number(request.headers.get("Content-Length") || "0");
  if (declared > MAX_BODY_BYTES) throw new HttpError(413, "body_too_large");
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) throw new HttpError(413, "body_too_large");
  let value;
  try {
    value = JSON.parse(text);
  } catch {
    throw new HttpError(400, "bad_json");
  }
  if (value === null || typeof value !== "object" || Array.isArray(value)) throw new HttpError(400, "bad_json");
  return value;
}

function requireString(value, re) {
  if (typeof value !== "string" || !re.test(value)) throw new HttpError(400, "bad_request");
  return value;
}

function json(value, status = 200, headers = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store", ...headers },
  });
}

function now() {
  return Math.floor(Date.now() / 1000);
}

function hex(bytes) {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function b64url(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromB64url(text) {
  const s = atob(text.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (text.length % 4)) % 4));
  return Uint8Array.from(s, (c) => c.charCodeAt(0));
}
