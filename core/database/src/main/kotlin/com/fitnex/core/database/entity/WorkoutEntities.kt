package com.fitnex.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitnex.core.model.ScheduledStatus
import com.fitnex.core.model.SessionStatus
import com.fitnex.core.model.SetSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** WORKOUT (a routine / template). Deleting it never deletes performed sessions. */
@Entity(tableName = "workouts", indices = [Index("user_id")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    @ColumnInfo(name = "scheduled_days") val scheduledDays: Set<DayOfWeek>,
    @ColumnInfo(name = "is_template") val isTemplate: Boolean,
    /** Set when FITNEX AI proposed this routine; holds the AI's explanation shown to the user. */
    @ColumnInfo(name = "ai_rationale") val aiRationale: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(WorkoutEntity::class, ["id"], ["workout_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workout_id", "position"]), Index("exercise_id")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val position: Int,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    @ColumnInfo(name = "target_reps") val targetReps: Int,
    @ColumnInfo(name = "target_weight_kg") val targetWeightKg: Float?,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    @Embedded val sync: SyncMeta,
)

/**
 * WORKOUT_SESSION. [workoutNameSnapshot] preserves history when the routine is renamed or
 * removed (spec §9 "los datos de entrenamiento deben conservarse incluso si cambia una rutina").
 */
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [ForeignKey(WorkoutEntity::class, ["id"], ["workout_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["user_id", "started_at"]), Index("workout_id"), Index("status")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String?,
    @ColumnInfo(name = "workout_name_snapshot") val workoutNameSnapshot: String,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "finished_at") val finishedAt: Instant?,
    /** Active training time, excluding pauses. */
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "paused_seconds") val pausedSeconds: Long,
    val status: SessionStatus,
    /** Index of the exercise on screen, persisted so a crashed session resumes in place. */
    @ColumnInfo(name = "current_exercise_index") val currentExerciseIndex: Int,
    /**
     * v3: JSON snapshot of the planned exercises (targets, rest) taken at start, so a session can
     * be resumed — and its history read — even if the routine is edited or deleted meanwhile.
     */
    @ColumnInfo(name = "plan_json") val planJson: String? = null,
    /** v3: when the current pause started; null while running. */
    @ColumnInfo(name = "paused_at") val pausedAt: Instant? = null,
    @Embedded val sync: SyncMeta,
)

/**
 * EXERCISE_SET. No FK on [exerciseId]: catalogue exercises are never hard-deleted and custom
 * ones are soft-deleted, so history stays intact; the name snapshot covers display needs.
 */
@Entity(
    tableName = "exercise_sets",
    foreignKeys = [ForeignKey(WorkoutSessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("session_id"), Index(value = ["exercise_id", "completed_at"])],
)
data class ExerciseSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "exercise_name_snapshot") val exerciseNameSnapshot: String,
    @ColumnInfo(name = "exercise_position") val exercisePosition: Int,
    @ColumnInfo(name = "set_number") val setNumber: Int,
    val repetitions: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Float,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int?,
    val source: SetSource,
    @ColumnInfo(name = "completed_at") val completedAt: Instant,
    @Embedded val sync: SyncMeta,
    /** Timed sets: seconds (v7). */
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    /** Cardio distance in metres (v7). */
    @ColumnInfo(name = "distance_m") val distanceMeters: Float? = null,
)

/** Calendar: a routine planned for a date (spec §17). */
@Entity(
    tableName = "scheduled_sessions",
    foreignKeys = [ForeignKey(WorkoutEntity::class, ["id"], ["workout_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["user_id", "date"]), Index("workout_id"), Index("completed_session_id")],
)
data class ScheduledSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "workout_id") val workoutId: String?,
    @ColumnInfo(name = "workout_name_snapshot") val workoutNameSnapshot: String,
    val date: LocalDate,
    val status: ScheduledStatus,
    @ColumnInfo(name = "completed_session_id") val completedSessionId: String?,
    val note: String?,
    @Embedded val sync: SyncMeta,
)

/**
 * A rest day the user explicitly planned. A day with no rows at all is "no activity recorded",
 * which the calendar and FITNEX AI must NOT present as rest (spec §17).
 */
@Entity(
    tableName = "planned_rest_days",
    indices = [Index(value = ["user_id", "date"], unique = true)],
)
data class PlannedRestDayEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val date: LocalDate,
    val note: String?,
    @Embedded val sync: SyncMeta,
)
