package com.fitnex.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.fitnex.core.database.entity.SyncMeta
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Optional body measurements in centimetres (adults only; never weight). Every value is optional:
 * the user records only what they want to follow.
 */
@Entity(tableName = "body_measurements", indices = [Index(value = ["user_id", "date"])])
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: LocalDate,
    @ColumnInfo(name = "waist_cm") val waistCm: Float?,
    @ColumnInfo(name = "chest_cm") val chestCm: Float?,
    @ColumnInfo(name = "hips_cm") val hipsCm: Float?,
    @ColumnInfo(name = "arm_cm") val armCm: Float?,
    @ColumnInfo(name = "thigh_cm") val thighCm: Float?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @Embedded val sync: SyncMeta,
)

@Dao
interface BodyDao {
    @Upsert suspend fun upsert(m: BodyMeasurementEntity)

    @Query("SELECT * FROM body_measurements WHERE user_id = :userId AND deleted_at IS NULL ORDER BY date DESC, created_at DESC")
    fun observe(userId: String): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE id = :id")
    suspend fun get(id: String): BodyMeasurementEntity?

    @Query("DELETE FROM body_measurements WHERE user_id = :userId")
    suspend fun deleteAllOf(userId: String)
}
