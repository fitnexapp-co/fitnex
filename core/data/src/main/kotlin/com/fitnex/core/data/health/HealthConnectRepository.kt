package com.fitnex.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNSUPPORTED }

/** Today's numbers from Health Connect; null where there is no data or no permission. */
data class HealthToday(val steps: Long?, val avgHeartRate: Long?, val latestWeightKg: Double?)

/**
 * Health Connect (Android's on-device health store): FITNEX reads steps, heart rate and weight
 * written by other apps and watches, and writes each finished workout as an exercise session.
 * Everything stays on the phone; nothing here goes to FITNEX's servers.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HealthAvailability.UNSUPPORTED
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun grantedPermissions(): Set<String> =
        client()?.let { runCatching { it.permissionController.getGrantedPermissions() }.getOrNull() }.orEmpty()

    suspend fun isConnected(): Boolean = grantedPermissions().isNotEmpty()

    suspend fun today(): HealthToday {
        val c = client() ?: return HealthToday(null, null, null)
        val granted = grantedPermissions()
        val zone = clock.zone
        val start = LocalDate.now(clock).atStartOfDay(zone).toInstant()
        val now = clock.instant()
        val range = TimeRangeFilter.between(start, now)
        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            runCatching { c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] }.getOrNull()
        } else null
        val heart = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            runCatching { c.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), range))[HeartRateRecord.BPM_AVG] }.getOrNull()
        } else null
        val weight = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) {
            runCatching {
                c.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(now.minus(90, ChronoUnit.DAYS), now), ascendingOrder = false, pageSize = 1))
                    .records.firstOrNull()?.weight?.inKilograms
            }.getOrNull()
        } else null
        return HealthToday(steps, heart, weight)
    }

    /** Writes a finished FITNEX workout; silently skipped without permission. */
    suspend fun writeWorkout(id: String, title: String, start: Instant, end: Instant) {
        val c = client() ?: return
        if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) !in grantedPermissions()) return
        if (!end.isAfter(start)) return
        runCatching {
            c.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start, startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(start),
                        endTime = end, endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(end),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                        title = title,
                        metadata = Metadata.manualEntry(clientRecordId = "fitnex-$id"),
                    ),
                ),
            )
        }
    }
}
