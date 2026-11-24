package com.fitnex.core.database.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitnex.core.database.FitnexDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One row (or deletion) as it travels to and from the cloud. [data] is null for a bare deletion. */
data class SyncRecord(
    val table: String,
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val data: JsonObject?,
)

data class ApplyReport(val applied: Int, val skippedOlder: Int, val skippedInvalid: Int)

/**
 * Generic, schema-driven sync engine over raw SQLite rows ([SyncSchema]). Blocking: call from IO.
 * Writes go through [FitnexDatabase.runInTransaction], so Room's observers (screens) refresh.
 */
@Singleton
class SyncStore @Inject constructor(private val room: FitnexDatabase) {
    private val db: SupportSQLiteDatabase get() = room.openHelper.writableDatabase
    private val columnsCache = mutableMapOf<String, Set<String>>()

    /**
     * Data owners synced under one account: the account itself plus its "Entrenar juntos" profiles
     * (ids "<uid>~…"), including deleted ones whose deletions are still waiting to be uploaded.
     */
    fun scopeIds(accountId: String): List<String> {
        val prefix = accountId + PROFILE_SEPARATOR
        val sql = "SELECT id FROM users WHERE substr(id, 1, length(?1)) = ?1 " +
            "UNION SELECT DISTINCT user_id FROM sync_tombstones WHERE user_id IS NOT NULL AND substr(user_id, 1, length(?1)) = ?1"
        val companions = db.query(sql, arrayOf(prefix)).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        return listOf(accountId) + companions.sorted()
    }

    /** Local changes not yet acknowledged by the cloud, parents first, then bare deletions. */
    fun pendingChanges(userId: String, limit: Int): List<SyncRecord> {
        val out = mutableListOf<SyncRecord>()
        for (t in SyncSchema.tables) {
            if (out.size >= limit) break
            db.query("SELECT * FROM `${t.name}` WHERE (${t.scope}) AND sync_status != 'SYNCED' LIMIT ${limit - out.size}", arrayOf(userId)).use { c ->
                while (c.moveToNext()) out += c.toRecord(t)
            }
        }
        if (out.size < limit) {
            db.query("SELECT table_name, row_id, deleted_at FROM sync_tombstones WHERE user_id = ? LIMIT ${limit - out.size}", arrayOf(userId)).use { c ->
                while (c.moveToNext()) out += SyncRecord(c.getString(0), c.getString(1), c.getLong(2), deleted = true, data = null)
            }
        }
        return out
    }

    /** Emits whenever a synced table (or the tombstones) changes. */
    fun localChanges(): kotlinx.coroutines.flow.Flow<Set<String>> =
        room.invalidationTracker.createFlow(*(SyncSchema.tables.map { it.name } + "sync_tombstones").toTypedArray(), emitInitialState = false)

    fun pendingCount(userId: String): Int {
        var n = 0
        for (t in SyncSchema.tables) {
            n += db.query("SELECT COUNT(*) FROM `${t.name}` WHERE (${t.scope}) AND sync_status != 'SYNCED'", arrayOf(userId)).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
        return n + db.query("SELECT COUNT(*) FROM sync_tombstones WHERE user_id = ?", arrayOf(userId)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** The server stored these: mark them synced unless they changed again meanwhile (updated_at differs). */
    fun markPushed(records: List<SyncRecord>) = room.runInTransaction(Runnable {
        for (r in records) {
            val t = SyncSchema.byName[r.table] ?: continue
            if (r.data == null) {
                db.delete("sync_tombstones", "table_name = ? AND row_id = ? AND deleted_at = ?", arrayOf<Any?>(r.table, r.id, r.updatedAt))
                continue
            }
            val (where, args) = keyWhere(t, r.id)
            db.update(t.name, SQLiteDatabase.CONFLICT_NONE, synced(), "$where AND updated_at = ?", args + r.updatedAt)
            t.child?.let { c ->
                if (c.table.hasColumn("sync_status")) db.update(c.table, SQLiteDatabase.CONFLICT_NONE, synced(), "${c.parentColumn} = ?", arrayOf(r.id))
            }
        }
        // Owner-less tombstones come from cascades; the parent's tombstone already carries the deletion.
        db.delete("sync_tombstones", "user_id IS NULL", null)
    })

    /**
     * Applies cloud records: last-writer-wins against local rows that are still pending, deletions
     * win ties. Children are replaced with the parent. Rows that cannot be stored (a foreign key to
     * something never synced, a unique clash) are skipped and counted, never crash the sync.
     */
    fun applyRemote(records: List<SyncRecord>): ApplyReport = room.runInTransaction<ApplyReport> {
        var applied = 0
        var older = 0
        var invalid = 0
        db.execSQL("INSERT OR REPLACE INTO sync_apply_lock (id) VALUES (1)")
        try {
            val order = SyncSchema.tables.withIndex().associate { it.value.name to it.index }
            for (r in records.sortedBy { order[it.table] ?: Int.MAX_VALUE }) {
                val t = SyncSchema.byName[r.table]
                if (t == null || r.id.isEmpty()) { invalid++; continue }
                val (where, args) = keyWhere(t, r.id)
                if (args.size != t.keyColumns.size) { invalid++; continue }
                if (localWins(t, where, args, r)) { older++; continue }
                db.delete("sync_tombstones", "table_name = ? AND row_id = ?", arrayOf(t.name, r.id))
                if (r.deleted) {
                    db.delete(t.name, where, args)
                    applied++
                    continue
                }
                val data = r.data
                if (data == null) { invalid++; continue }
                if (write(t, where, args, data)) applied++ else invalid++
            }
        } finally {
            db.execSQL("DELETE FROM sync_apply_lock")
        }
        ApplyReport(applied, older, invalid)
    }

    /** After the cloud copy was erased but the account survived: upload everything again. */
    fun markAllPending(userId: String) = room.runInTransaction(Runnable {
        for (t in SyncSchema.tables) {
            db.execSQL("UPDATE `${t.name}` SET sync_status = 'PENDING_UPSERT' WHERE (${t.scope}) AND sync_status = 'SYNCED'", arrayOf(userId))
        }
    })

    /** Everything this user owns on the device, as one JSON document (spec §11, data portability). */
    fun export(userId: String): JsonObject = buildJsonObject {
        putJsonObject("tables") {
            for ((table, scope) in SyncSchema.exportScopes) {
                val rows = db.query("SELECT * FROM `$table` WHERE $scope", arrayOf(userId)).use { c ->
                    buildList { while (c.moveToNext()) add(c.rowJson()) }
                }
                put(table, JsonArray(rows))
            }
        }
    }

    // --- internals --------------------------------------------------------------------------

    private fun Cursor.toRecord(t: SyncSchema.Table): SyncRecord {
        val row = rowJson(exclude = setOf("sync_status"))
        val id = t.keyColumns.joinToString("|") { (row[it] as? JsonPrimitive)?.content.orEmpty() }
        val withChildren = t.child?.let { c ->
            val children = db.query("SELECT * FROM `${c.table}` WHERE ${c.parentColumn} = ? ORDER BY ${c.orderBy}", arrayOf(id)).use { cc ->
                buildList { while (cc.moveToNext()) add(cc.rowJson(exclude = setOf("sync_status"))) }
            }
            JsonObject(row + (CHILDREN to JsonArray(children)))
        } ?: row
        val deletedAt = row["deleted_at"]
        return SyncRecord(
            table = t.name,
            id = id,
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            deleted = deletedAt != null && deletedAt !is JsonNull,
            data = withChildren,
        )
    }

    private fun localWins(t: SyncSchema.Table, where: String, args: Array<Any?>, r: SyncRecord): Boolean {
        db.query("SELECT updated_at, sync_status FROM `${t.name}` WHERE $where", args).use { c ->
            if (c.moveToFirst()) {
                val localUpdated = c.getLong(0)
                val pending = c.getString(1) != "SYNCED"
                if (pending && (localUpdated > r.updatedAt || (localUpdated == r.updatedAt && !r.deleted))) return true
            }
        }
        db.query("SELECT deleted_at FROM sync_tombstones WHERE table_name = ? AND row_id = ?", arrayOf(t.name, r.id)).use { c ->
            if (c.moveToFirst() && c.getLong(0) >= r.updatedAt) return true
        }
        return false
    }

    private fun write(t: SyncSchema.Table, where: String, args: Array<Any?>, data: JsonObject): Boolean {
        val values = data.toValues(t.name)
        if (t.name.hasColumn("sync_status")) values.put("sync_status", "SYNCED")
        for ((column, target) in t.optionalRefs) {
            val ref = values.getAsString(column) ?: continue
            if (!exists(target, ref)) values.putNull(column)
        }
        return try {
            if (db.update(t.name, SQLiteDatabase.CONFLICT_ABORT, values, where, args) == 0) {
                db.insert(t.name, SQLiteDatabase.CONFLICT_ABORT, values)
            }
            t.child?.let { c ->
                db.delete(c.table, "${c.parentColumn} = ?", arrayOf(args[0]))
                (data[CHILDREN] as? JsonArray)?.forEach { child ->
                    val cv = child.jsonObject.toValues(c.table)
                    if (c.table.hasColumn("sync_status")) cv.put("sync_status", "SYNCED")
                    db.insert(c.table, SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    private fun exists(table: String, id: String): Boolean =
        db.query("SELECT 1 FROM `$table` WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }

    /** Key "a|b" → "col1 = ? AND col2 = ?" with its arguments. */
    private fun keyWhere(t: SyncSchema.Table, id: String): Pair<String, Array<Any?>> {
        val parts = if (t.keyColumns.size == 1) listOf(id) else id.split('|', limit = t.keyColumns.size)
        return t.keyColumns.joinToString(" AND ") { "`$it` = ?" } to parts.toTypedArray<Any?>()
    }

    private fun synced() = ContentValues().apply { put("sync_status", "SYNCED") }

    private fun String.hasColumn(column: String) = column in columnsOf(this)

    private fun columnsOf(table: String): Set<String> = columnsCache.getOrPut(table) {
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }
    }

    /** Only columns this schema version knows: a newer app version's extra fields are ignored safely. */
    private fun JsonObject.toValues(table: String): ContentValues {
        val known = columnsOf(table)
        return ContentValues().apply {
            for ((k, v) in this@toValues) {
                if (k == CHILDREN || k == "sync_status" || k !in known) continue
                putJson(k, v)
            }
        }
    }

    private companion object {
        const val CHILDREN = "__children"
    }
}

private fun ContentValues.putJson(key: String, v: JsonElement) {
    when {
        v is JsonNull -> putNull(key)
        v is JsonPrimitive && v.isString -> put(key, v.content)
        v is JsonPrimitive -> {
            val s = v.content
            when {
                s == "true" || s == "false" -> put(key, if (s == "true") 1L else 0L)
                s.any { it == '.' || it == 'e' || it == 'E' } -> put(key, s.toDouble())
                else -> s.toLongOrNull()?.let { put(key, it) } ?: put(key, s.toDouble())
            }
        }
        else -> put(key, v.toString()) // nested JSON is never produced by rowJson; keep it as text
    }
}

private fun Cursor.rowJson(exclude: Set<String> = emptySet()): JsonObject = buildJsonObject {
    for (i in 0 until columnCount) {
        val name = getColumnName(i)
        if (name in exclude) continue
        when (getType(i)) {
            Cursor.FIELD_TYPE_NULL -> put(name, JsonNull)
            Cursor.FIELD_TYPE_INTEGER -> put(name, getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> put(name, getDouble(i))
            Cursor.FIELD_TYPE_STRING -> put(name, getString(i))
            else -> Unit // no BLOB columns in the schema
        }
    }
}

/** Separates an account id from one of its "Entrenar juntos" profiles: "<uid>~<profile>". */
const val PROFILE_SEPARATOR = "~"
