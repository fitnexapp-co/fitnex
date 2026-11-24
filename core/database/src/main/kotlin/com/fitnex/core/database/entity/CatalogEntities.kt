package com.fitnex.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.Tracking

/**
 * MUSCLE. [id] is a stable slug (e.g. "pectoralis_major") shared by the 2D SVG paths
 * ([model2dId]) and the GLB mesh node names ([model3dId]) so both maps read the same data.
 */
@Entity(tableName = "muscles")
data class MuscleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val function: String,
    val region: AnatomicalRegion,
    @ColumnInfo(name = "model_2d_id") val model2dId: String,
    @ColumnInfo(name = "model_3d_id") val model3dId: String,
)

/**
 * EXERCISE. Built-in catalogue rows have a slug id and [ownerUserId] = null;
 * user-created exercises carry a UUID, an owner and sync metadata.
 */
@Entity(
    tableName = "exercises",
    indices = [Index("owner_user_id"), Index("name")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: List<String>,
    @ColumnInfo(name = "technique_tips") val techniqueTips: List<String>,
    @ColumnInfo(name = "common_mistakes") val commonMistakes: List<String>,
    val equipment: Set<Equipment>,
    val type: ExerciseType,
    val difficulty: Difficulty,
    /** Asset path or remote URL of the image/animation; null until media is provided. */
    @ColumnInfo(name = "media_uri") val mediaUri: String?,
    /** True when the camera rep counter has a validated detector for this exercise. */
    @ColumnInfo(name = "camera_rep_supported") val cameraRepSupported: Boolean,
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String?,
    @Embedded val sync: SyncMeta,
    /** How sets are logged: WEIGHT_REPS, REPS, TIME or TIME_DISTANCE (v7). */
    @ColumnInfo(defaultValue = "WEIGHT_REPS") val tracking: Tracking = Tracking.WEIGHT_REPS,
    /** Extra search words (English name, common names, tags), separated by "|" (v7). */
    @ColumnInfo(name = "search_terms", defaultValue = "") val searchTerms: String = "",
)

/** EXERCISE_MUSCLE: many-to-many with a participation level. */
@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exercise_id", "muscle_id"],
    foreignKeys = [
        ForeignKey(ExerciseEntity::class, ["id"], ["exercise_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MuscleEntity::class, ["id"], ["muscle_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("muscle_id")],
)
data class ExerciseMuscleEntity(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "muscle_id") val muscleId: String,
    val involvement: MuscleInvolvement,
)

@Entity(
    tableName = "favorite_exercises",
    primaryKeys = ["user_id", "exercise_id"],
    indices = [Index("exercise_id")],
)
data class FavoriteExerciseEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @Embedded val sync: SyncMeta,
)
