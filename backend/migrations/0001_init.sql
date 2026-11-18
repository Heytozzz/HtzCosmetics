-- Equipped cosmetics per verified Minecraft UUID (no other personal data).
CREATE TABLE IF NOT EXISTS equipped (
  uuid TEXT PRIMARY KEY,          -- 32 lowercase hex chars, from Mojang hasJoined
  ids TEXT NOT NULL,              -- JSON array of cosmetic ids
  updated_at INTEGER NOT NULL     -- unix seconds
);

-- Short-lived, single-use login challenges (deleted on use, and by a cron after 2 minutes).
CREATE TABLE IF NOT EXISTS challenges (
  server_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,             -- lowercase player name, only for the 2-minute check
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS challenges_created_at ON challenges (created_at);
