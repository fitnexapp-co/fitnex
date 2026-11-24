// Runs the real SQL of src/sync.js against SQLite (node:sqlite) through a tiny D1-shaped adapter.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { countAiRequest, erase, pull, push, validatePull, validatePush } from "../src/sync.js";

function freshDb() {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec(readFileSync(new URL("../migrations/0001_sync.sql", import.meta.url), "utf8"));
  const statement = (sql, args = []) => ({
    sql,
    args,
    bind: (...a) => statement(sql, a),
    all: async () => ({ results: sqlite.prepare(sql).all(...args) }),
    first: async () => sqlite.prepare(sql).get(...args) ?? null,
    run: async () => sqlite.prepare(sql).run(...args),
  });
  return {
    prepare: (sql) => statement(sql),
    batch: async (stmts) => {
      sqlite.exec("BEGIN");
      try {
        for (const s of stmts) sqlite.prepare(s.sql).run(...s.args);
        sqlite.exec("COMMIT");
      } catch (e) {
        sqlite.exec("ROLLBACK");
        throw e;
      }
    },
  };
}

const rec = (id, u, extra = {}) => ({ t: "water_logs", id, u, d: false, data: { id, quantity_ml: 250, updated_at: u }, ...extra });
const pullAll = async (db, uid, since = 0) => JSON.parse(await pull(db, uid, { since, limit: 500 }));

test("push then pull returns records with increasing sequence and the row data", async () => {
  const db = freshDb();
  await push(db, "u1", validatePush({ records: [rec("w1", 10), rec("w2", 11)] }));
  await push(db, "u1", validatePush({ records: [rec("w3", 12)] }));
  const page = await pullAll(db, "u1");
  assert.deepEqual(page.records.map((r) => [r.id, r.seq]), [["w1", 1], ["w2", 2], ["w3", 3]]);
  assert.equal(page.records[0].data.quantity_ml, 250);
  assert.equal(page.next, 3);
  assert.equal(page.hasMore, false);
  assert.equal((await pullAll(db, "u1", 2)).records.length, 1);
});

test("last writer wins, deletions win ties, and an older write never moves the cursor", async () => {
  const db = freshDb();
  await push(db, "u1", validatePush({ records: [rec("w1", 100)] }));
  await push(db, "u1", validatePush({ records: [rec("w1", 50, { data: { quantity_ml: 1 } })] })); // stale
  let page = await pullAll(db, "u1");
  assert.equal(page.records.length, 1);
  assert.equal(page.records[0].data.quantity_ml, 250);
  assert.equal(page.records[0].seq, 1);

  await push(db, "u1", validatePush({ records: [{ t: "water_logs", id: "w1", u: 100, d: true, data: null }] }));
  page = await pullAll(db, "u1");
  assert.equal(page.records[0].d, true);
  assert.equal(page.records[0].data, null);
});

test("users never see each other's records and erase only removes the caller's", async () => {
  const db = freshDb();
  await push(db, "u1", validatePush({ records: [rec("w1", 1)] }));
  await push(db, "u2", validatePush({ records: [rec("w9", 1)] }));
  assert.deepEqual((await pullAll(db, "u1")).records.map((r) => r.id), ["w1"]);
  await erase(db, "u1");
  assert.equal((await pullAll(db, "u1")).records.length, 0);
  assert.equal((await pullAll(db, "u2")).records.length, 1);
  await push(db, "u1", validatePush({ records: [rec("w2", 2)] }));
  assert.equal((await pullAll(db, "u1")).records[0].seq, 2, "sequence keeps growing after an erase");
});

test("pull pages report hasMore", async () => {
  const db = freshDb();
  await push(db, "u1", validatePush({ records: [rec("a", 1), rec("b", 1), rec("c", 1)] }));
  const first = JSON.parse(await pull(db, "u1", { since: 0, limit: 2 }));
  assert.equal(first.hasMore, true);
  const second = JSON.parse(await pull(db, "u1", { since: first.next, limit: 2 }));
  assert.deepEqual(second.records.map((r) => r.id), ["c"]);
  assert.equal(second.hasMore, false);
});

test("validation rejects unknown tables, bad types and oversized pushes", () => {
  assert.throws(() => validatePush({ records: [] }), /records required/);
  assert.throws(() => validatePush({ records: [{ ...rec("x", 1), t: "sqlite_master" }] }), /unknown table/);
  assert.throws(() => validatePush({ records: [{ ...rec("x", 1), u: "1" }] }), /updated_at/);
  assert.throws(() => validatePush({ records: [{ ...rec("x", 1), data: null }] }), /data required/);
  assert.throws(() => validatePush({ records: Array.from({ length: 201 }, (_, i) => rec(`r${i}`, 1)) }), /too many/);
  assert.throws(() => validatePush({ records: [rec("x", 1, { data: { blob: "x".repeat(70_000) } })] }), /too large/);
  assert.deepEqual(validatePull({}), { since: 0, limit: 500 });
  assert.equal(validatePull({ limit: 9999 }).limit, 500);
  assert.throws(() => validatePull({ since: -1 }), /since/);
});

test("the daily AI quota is shared across requests", async () => {
  const db = freshDb();
  const day = new Date("2026-11-20T10:00:00Z");
  await countAiRequest(db, "u1", day, 2);
  await countAiRequest(db, "u1", day, 2);
  await assert.rejects(countAiRequest(db, "u1", day, 2), /Daily AI limit/);
  await countAiRequest(db, "u1", new Date("2026-11-21T00:00:01Z"), 2); // new day
  await countAiRequest(db, "u2", day, 2); // other user
});
