package com.fitnex.core.data.body

import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.database.dao.BodyDao
import com.fitnex.core.database.dao.BodyMeasurementEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.model.SyncStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One set of optional body measurements, in centimetres (never weight). */
data class BodyMeasurement(
    val id: String,
    val date: LocalDate,
    val waistCm: Float? = null,
    val chestCm: Float? = null,
    val hipsCm: Float? = null,
    val armCm: Float? = null,
    val thighCm: Float? = null,
) {
    val isEmpty: Boolean get() = listOf(waistCm, chestCm, hipsCm, armCm, thighCm).all { it == null }
}

/** Plausible range for a body circumference, to reject typos (not people). */
val BODY_CM_RANGE = 15f..250f

@Singleton
class BodyMeasurementRepository @Inject constructor(
    private val dao: BodyDao,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    /** Newest first. */
    fun observe(userId: String): Flow<List<BodyMeasurement>> = dao.observe(userId).map { rows ->
        rows.map { BodyMeasurement(it.id, it.date, it.waistCm, it.chestCm, it.hipsCm, it.armCm, it.thighCm) }
    }

    /** Saves a new entry; values outside [BODY_CM_RANGE] are dropped. Returns null if nothing valid was given. */
    suspend fun add(userId: String, m: BodyMeasurement): String? {
        fun ok(v: Float?) = v?.takeIf { it in BODY_CM_RANGE }
        val clean = m.copy(waistCm = ok(m.waistCm), chestCm = ok(m.chestCm), hipsCm = ok(m.hipsCm), armCm = ok(m.armCm), thighCm = ok(m.thighCm))
        if (clean.isEmpty) return null
        val id = ids.newId()
        val now = clock.instant()
        dao.upsert(BodyMeasurementEntity(id, userId, clean.date, clean.waistCm, clean.chestCm, clean.hipsCm, clean.armCm, clean.thighCm, now, SyncMeta(now)))
        return id
    }

    suspend fun delete(id: String) {
        val old = dao.get(id) ?: return
        val now = clock.instant()
        dao.upsert(old.copy(sync = SyncMeta(now, SyncStatus.PENDING_DELETE, deletedAt = now)))
    }
}
