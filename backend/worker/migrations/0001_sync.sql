-- Phase 9: cloud copy of each user's data + global AI quota.
CREATE TABLE IF NOT EXISTS sync_records (
  uid TEXT NOT NULL,
  tbl TEXT NOT NULL,
  id TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  data TEXT,
  seq INTEGER NOT NULL,
  PRIMARY KEY (uid, tbl, id)
);
CREATE INDEX IF NOT EXISTS sync_records_by_seq ON sync_records (uid, seq);

CREATE TABLE IF NOT EXISTS sync_counters (
  uid TEXT PRIMARY KEY,
  seq INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_usage (
  uid TEXT NOT NULL,
  day TEXT NOT NULL,
  count INTEGER NOT NULL,
  PRIMARY KEY (uid, day)
);
