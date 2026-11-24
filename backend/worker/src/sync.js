// Cloud copy of each user's data (Phase 9) on Cloudflare D1.
// The server is a dumb, per-user record store; the app owns the schema. Rules, identical in the app's tests:
//  - every stored record gets a per-user, strictly increasing sequence number (pull cursor);
//  - last-writer-wins on updated_at; on a tie a deletion wins;
//  - a user only ever reads or writes rows under their own verified uid.
// Each operation is ONE or TWO SQL statements (D1 free plan: 50 queries per request), records travel
// as a JSON array bound to a single parameter and are expanded with json_each.
import { HttpError } from "../../functions/src/logic.js";

export const SYNC_TABLES = new Set([
  "users", "workouts", "workout_sessions", "exercise_sets", "scheduled_sessions", "planned_rest_days",
  "meals", "food_items", "water_logs", "recipes", "favorite_exercises",
  "exercises", // custom exercises (app v1.1.0+)
  "meal_plan_items", // weekly meal plan (app v1.6.0+)
  "body_measurements", // optional body measurements (app v1.6.0+)
]);

export const SYNC_LIMITS = {
  pushRecords: 200,
  recordBytes: 64 * 1024,
  pushBytes: 1_800_000, // D1 binds at most 2 MB per value
  pullPage: 500,
  aiRequestsPerDay: 150,
};

const isPlainObject = (v) => v !== null && typeof v === "object" && !Array.isArray(v);

/** Returns the records ready to bind ({t, id, u, d: 0|1, data: JSON text|null}) or throws HttpError 400/413. */
export function validatePush(body) {
  const records = body?.records;
  if (!Array.isArray(records) || records.length === 0) throw new HttpError(400, "invalid_records", "records required");
  if (records.length > SYNC_LIMITS.pushRecords) throw new HttpError(413, "too_many_records", "too many records");
  let total = 0;
  const out = records.map((r) => {
    if (!isPlainObject(r) || !SYNC_TABLES.has(r.t)) throw new HttpError(400, "invalid_table", "unknown table");
    if (typeof r.id !== "string" || r.id.length === 0 || r.id.length > 200) throw new HttpError(400, "invalid_id", "invalid id");
    if (!Number.isSafeInteger(r.u) || r.u < 0) throw new HttpError(400, "invalid_updated_at", "invalid updated_at");
    if (typeof r.d !== "boolean") throw new HttpError(400, "invalid_deleted", "invalid deleted flag");
    if (r.data != null && !isPlainObject(r.data)) throw new HttpError(400, "invalid_data", "data must be an object");
    if (!r.d && r.data == null) throw new HttpError(400, "invalid_data", "data required");
    const data = r.data == null ? null : JSON.stringify(r.data);
    if (data && data.length > SYNC_LIMITS.recordBytes) throw new HttpError(413, "record_too_large", "record too large");
    total += (data?.length ?? 0) + r.id.length + 64;
    return { t: r.t, id: r.id, u: r.u, d: r.d ? 1 : 0, data };
  });
  if (total > SYNC_LIMITS.pushBytes) throw new HttpError(413, "push_too_large", "push too large");
  return out;
}

export function validatePull(body) {
  const since = body?.since ?? 0;
  const limit = body?.limit ?? SYNC_LIMITS.pullPage;
  if (!Number.isSafeInteger(since) || since < 0) throw new HttpError(400, "invalid_since", "invalid since");
  if (!Number.isSafeInteger(limit) || limit < 1) throw new HttpError(400, "invalid_limit", "invalid limit");
  return { since, limit: Math.min(limit, SYNC_LIMITS.pullPage) };
}

/** Stores the records atomically (D1 batch = one transaction): sequence bump + LWW upsert. */
export async function push(db, uid, records) {
  const n = records.length;
  await db.batch([
    db.prepare("INSERT INTO sync_counters (uid, seq) VALUES (?1, ?2) ON CONFLICT(uid) DO UPDATE SET seq = seq + ?2").bind(uid, n),
    db.prepare(
      `INSERT INTO sync_records (uid, tbl, id, updated_at, deleted, data, seq)
       SELECT ?1, json_extract(j.value, '$.t'), json_extract(j.value, '$.id'), json_extract(j.value, '$.u'),
              json_extract(j.value, '$.d'), json_extract(j.value, '$.data'),
              (SELECT seq FROM sync_counters WHERE uid = ?1) - ?2 + j.key + 1
       FROM json_each(?3) AS j WHERE true
       ON CONFLICT (uid, tbl, id) DO UPDATE SET
         updated_at = excluded.updated_at, deleted = excluded.deleted, data = excluded.data, seq = excluded.seq
       WHERE excluded.updated_at > sync_records.updated_at
          OR (excluded.updated_at = sync_records.updated_at AND excluded.deleted >= sync_records.deleted)`,
    ).bind(uid, n, JSON.stringify(records)),
  ]);
  return { stored: n };
}

/**
 * Records after `since`, oldest first. The row JSON is spliced in as stored (no parse/re-stringify:
 * the Workers free plan allows 10 ms of CPU per request).
 */
export async function pull(db, uid, { since, limit }) {
  const { results } = await db
    .prepare("SELECT tbl, id, updated_at, deleted, data, seq FROM sync_records WHERE uid = ?1 AND seq > ?2 ORDER BY seq LIMIT ?3")
    .bind(uid, since, limit + 1)
    .all();
  const hasMore = results.length > limit;
  const page = hasMore ? results.slice(0, limit) : results;
  const next = page.length ? page[page.length - 1].seq : since;
  const items = page.map((r) =>
    `{"t":${JSON.stringify(r.tbl)},"id":${JSON.stringify(r.id)},"u":${r.updated_at},"d":${r.deleted ? "true" : "false"},"data":${r.data ?? "null"},"seq":${r.seq}}`,
  );
  return `{"records":[${items.join(",")}],"next":${next},"hasMore":${hasMore}}`;
}

/** Account deletion. The counter row stays so sequence numbers never go backwards. */
export async function erase(db, uid) {
  await db.batch([
    db.prepare("DELETE FROM sync_records WHERE uid = ?1").bind(uid),
    db.prepare("DELETE FROM ai_usage WHERE uid = ?1").bind(uid),
  ]);
  return { erased: true };
}

/** Global daily AI quota per user (all isolates share D1), on top of the per-isolate burst limiter. */
export async function countAiRequest(db, uid, now = new Date(), limit = SYNC_LIMITS.aiRequestsPerDay) {
  const day = now.toISOString().slice(0, 10);
  const row = await db
    .prepare("INSERT INTO ai_usage (uid, day, count) VALUES (?1, ?2, 1) ON CONFLICT (uid, day) DO UPDATE SET count = count + 1 RETURNING count")
    .bind(uid, day)
    .first();
  if ((row?.count ?? 0) > limit) throw new HttpError(429, "daily_limit", "Daily AI limit reached");
}
