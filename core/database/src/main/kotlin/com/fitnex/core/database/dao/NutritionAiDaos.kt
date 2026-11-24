package com.fitnex.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.fitnex.core.database.entity.AiConversationEntity
import com.fitnex.core.database.entity.AiMessageEntity
import com.fitnex.core.database.entity.FoodItemEntity
import com.fitnex.core.database.entity.MealEntity
import com.fitnex.core.database.entity.WaterLogEntity
import com.fitnex.core.database.relation.MealWithItems
import com.fitnex.core.model.MealType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    @Upsert suspend fun upsertMeal(meal: MealEntity)
    @Upsert suspend fun upsertFoodItems(items: List<FoodItemEntity>)
    @Upsert suspend fun upsertWater(log: WaterLogEntity)

    @Transaction
    suspend fun saveMeal(meal: MealEntity, items: List<FoodItemEntity>) {
        upsertMeal(meal)
        upsertFoodItems(items)
    }

    @Transaction
    @Query(
        """
        SELECT * FROM meals
        WHERE user_id = :userId AND deleted_at IS NULL AND date BETWEEN :from AND :to
        ORDER BY date, logged_at
        """,
    )
    fun observeMeals(userId: String, from: LocalDate, to: LocalDate): Flow<List<MealWithItems>>

    @Query(
        """
        SELECT COALESCE(SUM(quantity_ml), 0) FROM water_logs
        WHERE user_id = :userId AND deleted_at IS NULL AND timestamp >= :from AND timestamp < :to
        """,
    )
    fun observeWaterMl(userId: String, from: Instant, to: Instant): Flow<Int>

    @Query("SELECT * FROM water_logs WHERE user_id = :userId AND deleted_at IS NULL AND timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    fun observeWaterLogs(userId: String, from: Instant, to: Instant): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM meals WHERE user_id = :userId AND date = :date AND meal_type = :type AND deleted_at IS NULL LIMIT 1")
    suspend fun findMeal(userId: String, date: LocalDate, type: com.fitnex.core.model.MealType): MealEntity?

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getMeal(id: String): MealEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getFoodItem(id: String): FoodItemEntity?

    @Query("SELECT * FROM water_logs WHERE id = :id")
    suspend fun getWater(id: String): WaterLogEntity?

    /** Most recently logged catalog foods, for quick re-adding. */
    @Query(
        """
        SELECT fi.food_id FROM food_items fi JOIN meals m ON m.id = fi.meal_id
        WHERE m.user_id = :userId AND fi.food_id IS NOT NULL AND fi.deleted_at IS NULL AND m.deleted_at IS NULL
        GROUP BY fi.food_id ORDER BY MAX(m.logged_at) DESC LIMIT :limit
        """,
    )
    fun observeRecentFoodIds(userId: String, limit: Int): Flow<List<String>>

    // Recipes
    @Upsert suspend fun upsertRecipe(recipe: com.fitnex.core.database.entity.RecipeEntity)
    @Upsert suspend fun upsertRecipeItems(items: List<com.fitnex.core.database.entity.RecipeItemEntity>)
    @Query("DELETE FROM recipe_items WHERE recipe_id = :recipeId") suspend fun deleteRecipeItems(recipeId: String)

    @Transaction
    suspend fun saveRecipe(recipe: com.fitnex.core.database.entity.RecipeEntity, items: List<com.fitnex.core.database.entity.RecipeItemEntity>) {
        upsertRecipe(recipe)
        deleteRecipeItems(recipe.id)
        upsertRecipeItems(items)
    }

    @Transaction
    @Query("SELECT * FROM recipes WHERE user_id = :userId AND deleted_at IS NULL ORDER BY name")
    fun observeRecipes(userId: String): Flow<List<com.fitnex.core.database.relation.RecipeWithItems>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipe(id: String): com.fitnex.core.database.relation.RecipeWithItems?

    // Weekly meal plan
    @Upsert suspend fun upsertPlanItem(item: com.fitnex.core.database.entity.MealPlanItemEntity)

    @Query("SELECT * FROM meal_plan_items WHERE user_id = :userId AND deleted_at IS NULL ORDER BY day_of_week, meal_type, position")
    fun observePlan(userId: String): Flow<List<com.fitnex.core.database.entity.MealPlanItemEntity>>

    @Query("SELECT * FROM meal_plan_items WHERE user_id = :userId AND day_of_week = :day AND deleted_at IS NULL ORDER BY position")
    suspend fun planForDay(userId: String, day: Int): List<com.fitnex.core.database.entity.MealPlanItemEntity>

    @Query("SELECT * FROM meal_plan_items WHERE id = :id")
    suspend fun getPlanItem(id: String): com.fitnex.core.database.entity.MealPlanItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM meal_plan_items WHERE user_id = :userId AND day_of_week = :day AND meal_type = :mealType")
    suspend fun nextPlanPosition(userId: String, day: Int, mealType: MealType): Int
}

@Dao
interface AiDao {
    @Upsert suspend fun upsertConversation(conversation: AiConversationEntity)
    @Upsert suspend fun upsertMessage(message: AiMessageEntity)

    @Query("SELECT * FROM ai_conversations WHERE user_id = :userId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeConversations(userId: String): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :conversationId ORDER BY timestamp")
    fun observeMessages(conversationId: String): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversation_id = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun lastMessages(conversationId: String, limit: Int): List<AiMessageEntity>

    @Query("SELECT * FROM ai_conversations WHERE id = :id")
    suspend fun getConversation(id: String): AiConversationEntity?

    /** Messages go with it (ON DELETE CASCADE). */
    @Query("DELETE FROM ai_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
}
