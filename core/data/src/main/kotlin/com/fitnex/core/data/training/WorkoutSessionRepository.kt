package com.fitnex.core.data.training

import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.database.dao.CalendarDao
import com.fitnex.core.database.dao.CatalogDao
import com.fitnex.core.database.dao.SessionDao
import com.fitnex.core.database.entity.ExerciseSetEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WorkoutSessionEntity
import com.fitnex.core.model.ActiveSession
import com.fitnex.core.model.LoggedSet
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.PreviousPerformance
import com.fitnex.core.model.Routine
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.SessionStatus
import com.fitnex.core.model.SessionSummary
import com.fitnex.core.model.SetSource
import com.fitnex.core.model.TrainingMath
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Live workout persistence (spec §18): every action is written immediately, so nothing is lost. */
interface WorkoutSessionRepository {
    fun observeActive(userId: String): Flow<ActiveSession?>

    /** Starts a session from a routine. If one is already active, returns that one instead. */
    suspend fun start(userId: String, routine: Routine): String

    /** [durationSeconds] / [distanceMeters]: timed and cardio sets (reps can then be 0). */
    suspend fun logSet(
        sessionId: String, item: RoutineExercise, position: Int, reps: Int, weightKg: Float, source: SetSource = SetSource.MANUAL,
        durationSeconds: Int? = null, distanceMeters: Float? = null,
    ): LoggedSet
    suspend fun updateSet(setId: String, reps: Int, weightKg: Float, durationSeconds: Int? = null, distanceMeters: Float? = null)
    suspend fun deleteSet(setId: String)
    suspend fun setCurrentExercise(sessionId: String, index: Int)
    suspend fun pause(sessionId: String)
    suspend fun resume(sessionId: String)

    /** Finishes and returns the summary; a session without any set is discarded and returns null. */
    suspend fun finish(sessionId: String): SessionSummary?
    suspend fun discard(sessionId: String)

    suspend fun previousPerformance(userId: String, exerciseId: String, before: Instant): PreviousPerformance?
    suspend fun summary(sessionId: String): SessionSummary?
    /** Sets of these exercises in workouts finished before [before]: the bar to beat for records. */
    suspend fun historyBefore(userId: String, exerciseIds: List<String>, before: Instant): List<LoggedSet> = emptyList()
}

@Singleton
class RoomWorkoutSessionRepository @Inject constructor(
    private val sessions: SessionDao,
    private val catalog: CatalogDao,
    private val calendar: CalendarDao,
    private val ids: IdGenerator,
    private val clock: Clock,
) : WorkoutSessionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(userId: String): Flow<ActiveSession?> =
        sessions.observeActiveSession(userId).flatMapLatest { s ->
            if (s == null) flowOf(null) else sessions.observeSets(s.id).map { sets -> s.toActive(sets) }
        }

    override suspend fun start(userId: String, routine: Routine): String {
        sessions.getActiveSession(userId)?.let { return it.id }
        val now = clock.instant()
        val id = ids.newId()
        sessions.upsertSession(
            WorkoutSessionEntity(
                id = id, userId = userId, workoutId = routine.id, workoutNameSnapshot = routine.name,
                startedAt = now, finishedAt = null, durationSeconds = null, pausedSeconds = 0,
                status = SessionStatus.IN_PROGRESS, currentExerciseIndex = 0,
                planJson = encodePlan(routine.exercises), pausedAt = null, sync = SyncMeta(updatedAt = now),
            ),
        )
        return id
    }

    override suspend fun logSet(
        sessionId: String, item: RoutineExercise, position: Int, reps: Int, weightKg: Float, source: SetSource,
        durationSeconds: Int?, distanceMeters: Float?,
    ): LoggedSet {
        val now = clock.instant()
        // Highest number + 1 (deleteSet keeps the numbers contiguous, so this is also the count + 1).
        val lastNumber = sessions.getSets(sessionId).filter { it.exerciseId == item.exerciseId }.maxOfOrNull { it.setNumber } ?: 0
        val entity = ExerciseSetEntity(
            id = ids.newId(), sessionId = sessionId, exerciseId = item.exerciseId, exerciseNameSnapshot = item.exerciseName,
            exercisePosition = position, setNumber = lastNumber + 1, repetitions = reps.coerceAtLeast(0),
            weightKg = weightKg.coerceAtLeast(0f), restSeconds = item.restSeconds, source = source, completedAt = now,
            sync = SyncMeta(updatedAt = now),
            durationSeconds = durationSeconds?.coerceAtLeast(0), distanceMeters = distanceMeters?.coerceAtLeast(0f),
        )
        sessions.upsertSet(entity)
        touch(sessionId, now)
        return entity.toModel()
    }

    override suspend fun updateSet(setId: String, reps: Int, weightKg: Float, durationSeconds: Int?, distanceMeters: Float?) {
        val set = sessions.getSet(setId) ?: return
        val now = clock.instant()
        sessions.upsertSet(
            set.copy(
                repetitions = reps.coerceAtLeast(0), weightKg = weightKg.coerceAtLeast(0f), sync = SyncMeta(updatedAt = now),
                durationSeconds = durationSeconds?.coerceAtLeast(0) ?: set.durationSeconds, distanceMeters = distanceMeters?.coerceAtLeast(0f) ?: set.distanceMeters,
            ),
        )
    }

    /** Deletes the set and closes the gap: with sets 1, 2, 3, deleting 2 leaves 1 and 2 (not 1 and 3). */
    override suspend fun deleteSet(setId: String) {
        val deleted = sessions.getSet(setId) ?: return
        val now = clock.instant()
        sessions.softDeleteSet(setId, now)
        sessions.getSets(deleted.sessionId)
            .filter { it.exerciseId == deleted.exerciseId && it.setNumber > deleted.setNumber }
            .forEach { sessions.upsertSet(it.copy(setNumber = it.setNumber - 1, sync = SyncMeta(updatedAt = now))) }
    }

    override suspend fun setCurrentExercise(sessionId: String, index: Int) {
        val s = sessions.getSession(sessionId) ?: return
        sessions.upsertSession(s.copy(currentExerciseIndex = index.coerceAtLeast(0), sync = SyncMeta(updatedAt = clock.instant())))
    }

    override suspend fun pause(sessionId: String) {
        val s = sessions.getSession(sessionId) ?: return
        if (s.pausedAt != null) return
        val now = clock.instant()
        sessions.upsertSession(s.copy(status = SessionStatus.PAUSED, pausedAt = now, sync = SyncMeta(updatedAt = now)))
    }

    override suspend fun resume(sessionId: String) {
        val s = sessions.getSession(sessionId) ?: return
        val pausedAt = s.pausedAt ?: return
        val now = clock.instant()
        sessions.upsertSession(
            s.copy(
                status = SessionStatus.IN_PROGRESS, pausedAt = null,
                pausedSeconds = s.pausedSeconds + Duration.between(pausedAt, now).seconds,
                sync = SyncMeta(updatedAt = now),
            ),
        )
    }

    override suspend fun finish(sessionId: String): SessionSummary? {
        val s = sessions.getSession(sessionId) ?: return null
        val sets = sessions.getSets(sessionId)
        if (sets.isEmpty()) {
            discard(sessionId)
            return null
        }
        val now = clock.instant()
        val active = s.toActive(sets)
        sessions.upsertSession(
            s.copy(
                status = SessionStatus.FINISHED, finishedAt = now, durationSeconds = active.elapsed(now).seconds,
                pausedSeconds = s.pausedSeconds + (s.pausedAt?.let { Duration.between(it, now).seconds } ?: 0),
                pausedAt = null, sync = SyncMeta(updatedAt = now),
            ),
        )
        // Spec §21: a routine scheduled for that day is now done.
        s.workoutId?.let { routineId ->
            calendar.markCompleted(s.userId, s.startedAt.atZone(clock.zone).toLocalDate(), routineId, s.id, now)
        }
        return summary(sessionId)
    }

    override suspend fun discard(sessionId: String) {
        val s = sessions.getSession(sessionId) ?: return
        val now = clock.instant()
        sessions.upsertSession(s.copy(status = SessionStatus.ABANDONED, pausedAt = null, sync = SyncMeta(updatedAt = now)))
    }

    override suspend fun previousPerformance(userId: String, exerciseId: String, before: Instant): PreviousPerformance? {
        val sets = sessions.finishedSetsBefore(userId, listOf(exerciseId), before)
        if (sets.isEmpty()) return null
        val lastSession = sets.maxBy { it.completedAt }.sessionId
        val last = sets.filter { it.sessionId == lastSession }.sortedBy { it.setNumber }
        return PreviousPerformance(last.first().completedAt, last.map { it.toModel() })
    }

    override suspend fun historyBefore(userId: String, exerciseIds: List<String>, before: Instant): List<LoggedSet> =
        if (exerciseIds.isEmpty()) emptyList() else sessions.finishedSetsBefore(userId, exerciseIds, before).map { it.toModel() }

    override suspend fun summary(sessionId: String): SessionSummary? {
        val s = sessions.getSession(sessionId) ?: return null
        val sets = sessions.getSets(sessionId).map { it.toModel() }
        if (sets.isEmpty()) return null
        val exerciseIds = sets.map { it.exerciseId }.distinct()
        val history = sessions.finishedSetsBefore(s.userId, exerciseIds, s.startedAt).map { it.toModel() }

        val muscles = catalog.getMuscles().associateBy { it.id }
        val links = catalog.getExercises(exerciseIds).flatMap { it.muscles }
        val worked = links
            .sortedBy { it.involvement.ordinal }
            .filter { it.involvement != MuscleInvolvement.STABILIZER }
            .mapNotNull { muscles[it.muscleId]?.toModel() }
            .distinctBy { it.id }

        val loaded = sets.any { it.weightKg > 0f }
        return SessionSummary(
            sessionId = s.id,
            routineName = s.workoutNameSnapshot,
            duration = Duration.ofSeconds(s.durationSeconds ?: s.toActive(emptyList()).elapsed(clock.instant()).seconds),
            exercisesCompleted = exerciseIds.size,
            sets = sets.size,
            totalReps = sets.sumOf { it.reps },
            volumeKg = if (loaded) TrainingMath.volume(sets) else null,
            records = TrainingMath.newRecords(sets, history),
            musclesWorked = worked,
            timedSeconds = sets.sumOf { it.durationSeconds ?: 0 },
            distanceMeters = sets.sumOf { (it.distanceMeters ?: 0f).toDouble() }.toFloat(),
        )
    }

    private suspend fun touch(sessionId: String, now: Instant) {
        sessions.getSession(sessionId)?.let { sessions.upsertSession(it.copy(sync = SyncMeta(updatedAt = now))) }
    }

    private fun WorkoutSessionEntity.toActive(sets: List<ExerciseSetEntity>) = ActiveSession(
        id = id, routineId = workoutId, routineName = workoutNameSnapshot, startedAt = startedAt,
        pausedSeconds = pausedSeconds, pausedAt = pausedAt, currentExerciseIndex = currentExerciseIndex,
        plan = decodePlan(planJson), sets = sets.map { it.toModel() },
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(PlanItemDto.serializer())

        internal fun encodePlan(items: List<RoutineExercise>): String = json.encodeToString(
            serializer,
            items.map { PlanItemDto(it.id, it.exerciseId, it.exerciseName, it.sets, it.reps, it.restSeconds, it.targetWeightKg) },
        )

        internal fun decodePlan(text: String?): List<RoutineExercise> =
            if (text.isNullOrBlank()) emptyList() else json.decodeFromString(serializer, text).map {
                RoutineExercise(it.id, it.exerciseId, it.name, it.sets, it.reps, it.rest, it.weight)
            }
    }
}

@Serializable
internal data class PlanItemDto(
    val id: String,
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val rest: Int,
    val weight: Float? = null,
)

internal fun ExerciseSetEntity.toModel() = LoggedSet(
    id = id, sessionId = sessionId, exerciseId = exerciseId, exerciseName = exerciseNameSnapshot,
    exercisePosition = exercisePosition, setNumber = setNumber, reps = repetitions, weightKg = weightKg,
    completedAt = completedAt, source = source, durationSeconds = durationSeconds, distanceMeters = distanceMeters,
)
