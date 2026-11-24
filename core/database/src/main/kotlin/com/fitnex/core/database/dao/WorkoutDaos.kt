package com.fitnex.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fitnex.core.database.entity.ExerciseSetEntity
import com.fitnex.core.database.entity.PlannedRestDayEntity
import com.fitnex.core.database.entity.ScheduledSessionEntity
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.entity.WorkoutExerciseEntity
import com.fitnex.core.database.entity.WorkoutSessionEntity
import com.fitnex.core.database.relation.MuscleSetCount
import com.fitnex.core.database.relation.SessionWithSets
import com.fitnex.core.database.relation.WorkoutWithExercises
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Upsert suspend fun upsertWorkout(workout: WorkoutEntity)
    @Upsert suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE workout_id = :workoutId")
    suspend fun deleteExercisesOf(workoutId: String)

    /** Replaces the full exercise list atomically (used by the routine editor's reorder/save). */
    @Transaction
    suspend fun saveWorkout(workout: WorkoutEntity, exercises: List<WorkoutExerciseEntity>) {
        upsertWorkout(workout)
        deleteExercisesOf(workout.id)
        upsertWorkoutExercises(exercises)
    }

    @Transaction
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND deleted_at IS NULL ORDER BY name COLLATE NOCASE")
    fun observeWorkouts(userId: String): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id AND deleted_at IS NULL")
    fun observeWorkout(id: String): Flow<WorkoutWithExercises?>

    @Query(
        "UPDATE workouts SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING_DELETE' WHERE id = :id",
    )
    suspend fun softDelete(id: String, now: Instant)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun hardDelete(id: String)
}

@Dao
interface SessionDao {
    @Upsert suspend fun upsertSession(session: WorkoutSessionEntity)
    @Upsert suspend fun upsertSet(set: ExerciseSetEntity)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    /** The session to restore after a crash: at most one unfinished session per user. */
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE user_id = :userId AND status IN ('IN_PROGRESS','PAUSED') AND deleted_at IS NULL
        ORDER BY started_at DESC LIMIT 1
        """,
    )
    fun observeActiveSession(userId: String): Flow<WorkoutSessionEntity?>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE user_id = :userId AND status IN ('IN_PROGRESS','PAUSED') AND deleted_at IS NULL
        ORDER BY started_at DESC LIMIT 1
        """,
    )
    suspend fun getActiveSession(userId: String): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSessionWithSets(id: String): Flow<SessionWithSets?>

    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE user_id = :userId AND status = 'FINISHED' AND deleted_at IS NULL
          AND started_at >= :from AND started_at < :to
        ORDER BY started_at DESC
        """,
    )
    fun observeFinishedSessionsBetween(userId: String, from: Instant, to: Instant): Flow<List<SessionWithSets>>

    /** Full history of one exercise (per-exercise stats page, PRs, "última sesión"). */
    @Query(
        """
        SELECT s.* FROM exercise_sets s
        JOIN workout_sessions ws ON ws.id = s.session_id
        WHERE ws.user_id = :userId AND s.exercise_id = :exerciseId
          AND s.deleted_at IS NULL AND ws.deleted_at IS NULL
        ORDER BY s.completed_at ASC
        """,
    )
    fun observeExerciseHistory(userId: String, exerciseId: String): Flow<List<ExerciseSetEntity>>

    @Query(
        """
        SELECT em.muscle_id AS muscle_id, em.involvement AS involvement,
               COUNT(s.id) AS set_count, COUNT(DISTINCT s.session_id) AS session_count
        FROM exercise_sets s
        JOIN workout_sessions ws ON ws.id = s.session_id
        JOIN exercise_muscles em ON em.exercise_id = s.exercise_id
        WHERE ws.user_id = :userId AND ws.deleted_at IS NULL AND s.deleted_at IS NULL
          AND s.completed_at >= :from AND s.completed_at < :to
        GROUP BY em.muscle_id, em.involvement
        """,
    )
    fun observeMuscleSetCounts(userId: String, from: Instant, to: Instant): Flow<List<MuscleSetCount>>

    @Query(
        "UPDATE exercise_sets SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING_DELETE' WHERE id = :setId",
    )
    suspend fun softDeleteSet(setId: String, now: Instant)

    @Query("SELECT * FROM exercise_sets WHERE session_id = :sessionId AND deleted_at IS NULL ORDER BY completed_at")
    fun observeSets(sessionId: String): Flow<List<ExerciseSetEntity>>

    @Query("SELECT * FROM exercise_sets WHERE session_id = :sessionId AND deleted_at IS NULL ORDER BY completed_at")
    suspend fun getSets(sessionId: String): List<ExerciseSetEntity>

    @Query("SELECT * FROM exercise_sets WHERE id = :id")
    suspend fun getSet(id: String): ExerciseSetEntity?

    /** All finished sessions with their sets (deleted sets are filtered by the repository). */
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE user_id = :userId AND status = 'FINISHED' AND deleted_at IS NULL ORDER BY started_at")
    fun observeAllFinished(userId: String): Flow<List<SessionWithSets>>

    /** Sets of FINISHED sessions that started before [before] — the baseline for records and "last time". */
    @Query(
        """
        SELECT s.* FROM exercise_sets s
        JOIN workout_sessions ws ON ws.id = s.session_id
        WHERE ws.user_id = :userId AND s.exercise_id IN (:exerciseIds) AND ws.status = 'FINISHED'
          AND ws.started_at < :before AND s.deleted_at IS NULL AND ws.deleted_at IS NULL
        ORDER BY s.completed_at
        """,
    )
    suspend fun finishedSetsBefore(userId: String, exerciseIds: List<String>, before: Instant): List<ExerciseSetEntity>
}

@Dao
interface CalendarDao {
    @Upsert suspend fun upsertScheduled(item: ScheduledSessionEntity)
    @Upsert suspend fun upsertRestDay(item: PlannedRestDayEntity)

    @Query(
        """
        SELECT * FROM scheduled_sessions
        WHERE user_id = :userId AND deleted_at IS NULL AND date BETWEEN :from AND :to
        ORDER BY date
        """,
    )
    fun observeScheduled(userId: String, from: LocalDate, to: LocalDate): Flow<List<ScheduledSessionEntity>>

    @Query(
        """
        SELECT * FROM planned_rest_days
        WHERE user_id = :userId AND deleted_at IS NULL AND date BETWEEN :from AND :to
        ORDER BY date
        """,
    )
    fun observeRestDays(userId: String, from: LocalDate, to: LocalDate): Flow<List<PlannedRestDayEntity>>

    /** Includes soft-deleted rows: (user_id, date) is unique, so a removed rest day is revived, not re-inserted. */
    @Query("SELECT * FROM planned_rest_days WHERE user_id = :userId AND date = :date")
    suspend fun getRestDay(userId: String, date: LocalDate): PlannedRestDayEntity?

    @Query("SELECT * FROM scheduled_sessions WHERE id = :id")
    suspend fun getScheduled(id: String): ScheduledSessionEntity?

    @Query(
        """
        UPDATE scheduled_sessions SET status = 'COMPLETED', completed_session_id = :sessionId,
            updated_at = :now, sync_status = 'PENDING_UPSERT'
        WHERE user_id = :userId AND date = :date AND workout_id = :workoutId AND status = 'SCHEDULED' AND deleted_at IS NULL
        """,
    )
    suspend fun markCompleted(userId: String, date: LocalDate, workoutId: String, sessionId: String, now: Instant)
}
