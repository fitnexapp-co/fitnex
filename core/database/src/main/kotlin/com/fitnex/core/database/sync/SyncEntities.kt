package com.fitnex.core.database.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A row that was hard-deleted locally and whose deletion has not reached the cloud yet.
 * Written by SQLite triggers ([SyncSchema.triggers]), so no DAO can forget to record one;
 * removed once the server acknowledges it.
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["table_name", "row_id"])
data class SyncTombstoneEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    /** Null when the owner could not be resolved (a child removed by a cascade): the parent's tombstone covers it. */
    @ColumnInfo(name = "user_id") val userId: String?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)

/** Per-user sync progress on this device. Its absence means "never synced here" (restore on sign-in). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    /** Highest server sequence already applied locally. */
    val cursor: Long,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Instant?,
)

/**
 * While a row exists here the tombstone triggers stay silent: deletions that come FROM the cloud
 * must not be echoed back to it. Only ever set inside a transaction.
 */
@Entity(tableName = "sync_apply_lock")
data class SyncApplyLockEntity(@PrimaryKey val id: Int = 1)
