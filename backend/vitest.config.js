import path from "node:path";
import { fileURLToPath } from "node:url";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

const dir = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [
    cloudflareTest(async () => ({
      wrangler: { configPath: "./wrangler.toml" },
      miniflare: {
        bindings: {
          TEST_MIGRATIONS: await readD1Migrations(path.join(dir, "migrations")),
          TOKEN_SECRET: "test-secret-test-secret-test-secret-0123456789",
        },
      },
    })),
  ],
  test: {
    setupFiles: ["./test/apply-migrations.js"],
  },
});
