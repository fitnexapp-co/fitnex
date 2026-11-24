package com.fitnex.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Removes every row owned by one user (spec §11 "eliminación de datos"). Child tables
 * (sets, routine exercises, food items, AI messages) go with their parents via ON DELETE CASCADE.
 * The built-in exercise/muscle catalogue is shared, not personal, and is kept.
 */
@Dao
interface AccountDao {
    @Query("DELETE FROM workout_sessions WHERE user_id = :userId") suspend fun deleteSessions(userId: String)
    @Query("DELETE FROM scheduled_sessions WHERE user_id = :userId") suspend fun deleteScheduled(userId: String)
    @Query("DELETE FROM planned_rest_days WHERE user_id = :userId") suspend fun deleteRestDays(userId: String)
    @Query("DELETE FROM workouts WHERE user_id = :userId") suspend fun deleteWorkouts(userId: String)
    @Query("DELETE FROM favorite_exercises WHERE user_id = :userId") suspend fun deleteFavorites(userId: String)
    @Query("DELETE FROM exercises WHERE owner_user_id = :userId") suspend fun deleteCustomExercises(userId: String)
    @Query("DELETE FROM meals WHERE user_id = :userId") suspend fun deleteMeals(userId: String)
    @Query("DELETE FROM water_logs WHERE user_id = :userId") suspend fun deleteWater(userId: String)
    @Query("DELETE FROM recipes WHERE user_id = :userId") suspend fun deleteRecipes(userId: String)
    @Query("DELETE FROM meal_plan_items WHERE user_id = :userId") suspend fun deleteMealPlan(userId: String)
    @Query("DELETE FROM body_measurements WHERE user_id = :userId") suspend fun deleteMeasurements(userId: String)
    @Query("DELETE FROM ai_conversations WHERE user_id = :userId") suspend fun deleteConversations(userId: String)
    @Query("DELETE FROM users WHERE id = :userId") suspend fun deleteUser(userId: String)

    /** The account is gone (and its cloud copy with it): nothing left to sync. */
    @Query("DELETE FROM sync_tombstones WHERE user_id = :userId OR user_id IS NULL") suspend fun deleteTombstones(userId: String)
    @Query("DELETE FROM sync_state WHERE user_id = :userId") suspend fun deleteSyncState(userId: String)

    /**
     * An "Entrenar juntos" profile removed while its account lives on: its rows go, but the
     * deletions they leave behind (tombstones) stay, so the cloud copy is deleted at the next sync.
     */
    @Transaction
    suspend fun deleteProfileData(userId: String) {
        deleteSessions(userId)
        deleteScheduled(userId)
        deleteRestDays(userId)
        deleteWorkouts(userId)
        deleteFavorites(userId)
        deleteCustomExercises(userId)
        deleteMeals(userId)
        deleteWater(userId)
        deleteRecipes(userId)
        deleteMealPlan(userId)
        deleteMeasurements(userId)
        deleteConversations(userId)
        deleteUser(userId)
    }

    /** Sessions are deleted before routines so no SET_NULL update is needed on rows about to go. */
    @Transaction
    suspend fun deleteAllDataOf(userId: String) {
        deleteSessions(userId)
        deleteScheduled(userId)
        deleteRestDays(userId)
        deleteWorkouts(userId)
        deleteFavorites(userId)
        deleteCustomExercises(userId)
        deleteMeals(userId)
        deleteWater(userId)
        deleteRecipes(userId)
        deleteMealPlan(userId)
        deleteMeasurements(userId)
        deleteConversations(userId)
        deleteUser(userId)
        deleteTombstones(userId)
        deleteSyncState(userId)
    }

    // ---- "Reiniciar estadísticas": training history only, for the account and all its profiles.
    // Soft deletes (PENDING_DELETE) so the next sync removes the cloud copy and the other devices'.

    @Query(
        "UPDATE exercise_sets SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING_DELETE' " +
            "WHERE deleted_at IS NULL AND session_id IN (SELECT id FROM workout_sessions WHERE user_id IN (:userIds))",
    )
    suspend fun softDeleteSetsOf(userIds: List<String>, now: java.time.Instant)

    @Query(
        "UPDATE workout_sessions SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING_DELETE' " +
            "WHERE deleted_at IS NULL AND user_id IN (:userIds)",
    )
    suspend fun softDeleteSessionsOf(userIds: List<String>, now: java.time.Instant)

    /** Calendar days marked as done by a workout; planned future sessions stay. */
    @Query(
        "UPDATE scheduled_sessions SET deleted_at = :now, updated_at = :now, sync_status = 'PENDING_DELETE' " +
            "WHERE deleted_at IS NULL AND completed_session_id IS NOT NULL AND user_id IN (:userIds)",
    )
    suspend fun softDeleteCompletedScheduledOf(userIds: List<String>, now: java.time.Instant)

    @Transaction
    suspend fun resetTrainingHistory(userIds: List<String>, now: java.time.Instant) {
        softDeleteSetsOf(userIds, now)
        softDeleteSessionsOf(userIds, now)
        softDeleteCompletedScheduledOf(userIds, now)
    }
}