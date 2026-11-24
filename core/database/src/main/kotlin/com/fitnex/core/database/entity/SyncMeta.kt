package com.fitnex.core.database.entity

import androidx.room.ColumnInfo
import com.fitnex.core.model.SyncStatus
import java.time.Instant

/**
 * Embedded in every user-owned row (local-first sync, spec §10).
 *
 * Conflict rule: last-writer-wins on [updatedAt], with deletions winning ties.
 * Rows are never hard-deleted until the deletion has been acknowledged by the backend
 * ([SyncStatus.PENDING_DELETE] + [deletedAt]); ids are client-generated UUIDs, so
 * re-sending a row is idempotent and cannot create duplicates.
 */
data class SyncMeta(
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING_UPSERT,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)
