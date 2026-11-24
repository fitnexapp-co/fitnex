package com.fitnex.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fitnex.core.database.entity.ExerciseEntity
import com.fitnex.core.database.entity.ExerciseMuscleEntity
import com.fitnex.core.database.entity.FavoriteExerciseEntity
import com.fitnex.core.database.entity.MuscleEntity
import com.fitnex.core.database.entity.UserEntity
import com.fitnex.core.database.relation.ExerciseName
import com.fitnex.core.database.relation.ExerciseWithMuscles
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id AND deleted_at IS NULL")
    fun observe(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun get(id: String): UserEntity?

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun hardDelete(id: String)

    /** "Entrenar juntos" profiles of one account: ids start with "<ownerId>~". */
    @Query("SELECT * FROM users WHERE substr(id, 1, length(:prefix)) = :prefix AND deleted_at IS NULL ORDER BY created_at")
    fun observeCompanions(prefix: String): Flow<List<UserEntity>>

    @Query("SELECT id FROM users WHERE substr(id, 1, length(:prefix)) = :prefix")
    suspend fun companionIds(prefix: String): List<String>
}

@Dao
interface CatalogDao {
    @Upsert suspend fun upsertMuscles(muscles: List<MuscleEntity>)
    @Upsert suspend fun upsertExercises(exercises: List<ExerciseEntity>)
    @Upsert suspend fun upsertExerciseMuscles(links: List<ExerciseMuscleEntity>)

    @Query("SELECT COUNT(*) FROM exercises WHERE owner_user_id IS NULL")
    suspend fun builtInExerciseCount(): Int

    @Query("UPDATE exercises SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING_DELETE' WHERE id = :id AND owner_user_id IS NOT NULL")
    suspend fun softDeleteCustom(id: String, now: java.time.Instant)

    @Query("DELETE FROM exercise_muscles WHERE exercise_id IN (:exerciseIds)")
    suspend fun deleteLinksFor(exerciseIds: List<String>)

    /**
     * Replaces the built-in catalogue in one transaction. Ids are stable slugs, so rows are
     * updated in place and every routine/set referencing them keeps working.
     */
    @Transaction
    suspend fun replaceCatalog(
        muscles: List<MuscleEntity>,
        exercises: List<ExerciseEntity>,
        links: List<ExerciseMuscleEntity>,
    ) {
        upsertMuscles(muscles)
        upsertExercises(exercises)
        deleteLinksFor(exercises.map { it.id })
        upsertExerciseMuscles(links)
    }

    @Query("SELECT * FROM muscles")
    suspend fun getMuscles(): List<MuscleEntity>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getExercises(ids: List<String>): List<ExerciseWithMuscles>

    @Query("SELECT id, name FROM exercises")
    fun observeExerciseNames(): Flow<List<ExerciseName>>

    @Query("SELECT * FROM exercise_muscles")
    fun observeAllLinks(): Flow<List<ExerciseMuscleEntity>>

    @Query("SELECT COUNT(*) > 0 FROM favorite_exercises WHERE user_id = :userId AND exercise_id = :exerciseId AND deleted_at IS NULL")
    fun observeIsFavorite(userId: String, exerciseId: String): Flow<Boolean>

    @Query("SELECT * FROM muscles ORDER BY region, name")
    fun observeMuscles(): Flow<List<MuscleEntity>>

    /** Built-in catalogue + the user's own (non-deleted) exercises. */
    @Transaction
    @Query(
        """
        SELECT * FROM exercises
        WHERE deleted_at IS NULL AND (owner_user_id IS NULL OR owner_user_id = :userId)
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeExercises(userId: String): Flow<List<ExerciseWithMuscles>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeExercise(id: String): Flow<ExerciseWithMuscles?>

    @Transaction
    @Query(
        """
        SELECT e.* FROM exercises e
        JOIN exercise_muscles em ON em.exercise_id = e.id
        WHERE em.muscle_id = :muscleId AND e.deleted_at IS NULL
          AND (e.owner_user_id IS NULL OR e.owner_user_id = :userId)
        ORDER BY em.involvement, e.name COLLATE NOCASE
        """,
    )
    fun observeExercisesForMuscle(userId: String, muscleId: String): Flow<List<ExerciseWithMuscles>>

    @Upsert suspend fun upsertFavorite(favorite: FavoriteExerciseEntity)

    @Query("SELECT exercise_id FROM favorite_exercises WHERE user_id = :userId AND deleted_at IS NULL")
    fun observeFavoriteIds(userId: String): Flow<List<String>>
}
