// Refreshes src/mojang-keys.js from Mojang's public key list. Run: npm run update-mojang-keys
// (Mojang rarely rotates these keys; if logins start failing with "invalid_certificate", run this and redeploy.)
import { writeFileSync } from "node:fs";

const res = await fetch("https://api.minecraftservices.com/publickeys");
if (!res.ok) throw new Error(`Mojang answered HTTP ${res.status}`);
const data = await res.json();
const keys = (data.playerCertificateKeys || []).map((k) => k.publicKey);
if (keys.length === 0 || keys.some((k) => typeof k !== "string" || !/^[A-Za-z0-9+/]+={0,2}$/.test(k))) {
  throw new Error("Unexpected key list format; nothing was changed");
}
const today = new Date().toISOString().slice(0, 10);
writeFileSync(new URL("../src/mojang-keys.js", import.meta.url),
  `// Mojang's public keys that sign player certificates (the chat-signing keys every Minecraft account gets).
// Source: https://api.minecraftservices.com/publickeys -> playerCertificateKeys (fetched ${today}).
// Embedded because Mojang's servers refuse requests coming from Cloudflare Workers. Public, not secret.
export const MOJANG_PLAYER_CERTIFICATE_KEYS = [
${keys.map((k) => `  "${k}",`).join("\n")}
];
`);
console.log(`Saved ${keys.length} Mojang certificate keys. Now run: npm test && npm run deploy`);
