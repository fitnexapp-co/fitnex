package com.fitnex.core.database.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation
import com.fitnex.core.database.entity.ExerciseEntity
import com.fitnex.core.database.entity.ExerciseMuscleEntity
import com.fitnex.core.database.entity.ExerciseSetEntity
import com.fitnex.core.database.entity.FoodItemEntity
import com.fitnex.core.database.entity.MealEntity
import com.fitnex.core.database.entity.WorkoutEntity
import com.fitnex.core.database.entity.WorkoutExerciseEntity
import com.fitnex.core.database.entity.WorkoutSessionEntity
import com.fitnex.core.model.MuscleInvolvement

data class ExerciseWithMuscles(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exercise_id")
    val muscles: List<ExerciseMuscleEntity>,
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workout_id")
    val exercises: List<WorkoutExerciseEntity>,
)

data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val sets: List<ExerciseSetEntity>,
)

data class MealWithItems(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_id")
    val items: List<FoodItemEntity>,
)

data class RecipeWithItems(
    @Embedded val recipe: com.fitnex.core.database.entity.RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipe_id")
    val items: List<com.fitnex.core.database.entity.RecipeItemEntity>,
)

data class ExerciseName(val id: String, val name: String)

/** Raw aggregate; weighting by [MuscleInvolvement.weight] happens in Kotlin (single source of truth). */
data class MuscleSetCount(
    @ColumnInfo(name = "muscle_id") val muscleId: String,
    val involvement: MuscleInvolvement,
    @ColumnInfo(name = "set_count") val setCount: Int,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
)
