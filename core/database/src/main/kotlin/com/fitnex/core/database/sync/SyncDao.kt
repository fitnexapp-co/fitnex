package com.fitnex.core.database.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE user_id = :userId")
    suspend fun state(userId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE user_id = :userId")
    fun observeState(userId: String): Flow<SyncStateEntity?>

    @Upsert
    suspend fun saveState(state: SyncStateEntity)
}
