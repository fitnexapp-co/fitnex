package com.fitnex.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitnex.core.model.AiRole
import com.fitnex.core.model.AnalysisStatus
import com.fitnex.core.model.MealType
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "meals", indices = [Index(value = ["user_id", "date"])])
data class MealEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "meal_type") val mealType: MealType,
    val date: LocalDate,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    val description: String?,
    /** Local content URI or Storage path; only set when the user chose to keep the photo. */
    @ColumnInfo(name = "image_reference") val imageReference: String?,
    @ColumnInfo(name = "analysis_status") val analysisStatus: AnalysisStatus,
    @Embedded val sync: SyncMeta,
)

/**
 * FOOD_ITEM. All nutrient values are ESTIMATES (photo analysis or database lookup);
 * [confidence] is 0..1 from the analysis, null for manual entries.
 */
@Entity(
    tableName = "food_items",
    foreignKeys = [ForeignKey(MealEntity::class, ["id"], ["meal_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("meal_id")],
)
data class FoodItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "meal_id") val mealId: String,
    /** Catalog food (assets/nutrition/foods.json) this entry came from; null for custom/photo entries. */
    @ColumnInfo(name = "food_id") val foodId: String? = null,
    val name: String,
    @ColumnInfo(name = "portion_description") val portionDescription: String,
    @ColumnInfo(name = "portion_grams") val portionGrams: Float?,
    @ColumnInfo(name = "protein_g") val proteinG: Float?,
    @ColumnInfo(name = "carbs_g") val carbsG: Float?,
    @ColumnInfo(name = "fat_g") val fatG: Float?,
    @ColumnInfo(name = "fiber_g") val fiberG: Float?,
    @ColumnInfo(name = "kcal_estimate") val kcalEstimate: Float?,
    /** Vitamins/minerals as a JSON object {"vitamin_c_mg": 12.0, ...}. */
    @ColumnInfo(name = "micronutrients_json") val micronutrientsJson: String?,
    val confidence: Float?,
    @ColumnInfo(name = "user_edited") val userEdited: Boolean,
    @Embedded val sync: SyncMeta,
)

/** A user's recipe: ingredients from the catalog, divided into [servings]. */
@Entity(tableName = "recipes", indices = [Index("user_id")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val name: String,
    val servings: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "recipe_items",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipe_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recipe_id")],
)
data class RecipeItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    val grams: Float,
    val position: Int,
)

/**
 * One planned item of the weekly meal plan: a catalog food ([foodId] + [grams]) or a recipe
 * ([recipeId] + [servings]) on a weekday ([dayOfWeek] 1 = Monday … 7 = Sunday) and meal.
 * Nutrients are computed when the plan is copied to the diary, so they follow the catalog/recipe.
 */
@Entity(tableName = "meal_plan_items", indices = [Index("user_id")])
data class MealPlanItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "meal_type") val mealType: MealType,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "recipe_id") val recipeId: String?,
    val name: String,
    val grams: Float?,
    val servings: Float?,
    val portion: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @Embedded val sync: SyncMeta,
)

@Entity(tableName = "water_logs", indices = [Index(value = ["user_id", "timestamp"])])
data class WaterLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "quantity_ml") val quantityMl: Int,
    val timestamp: Instant,
    @Embedded val sync: SyncMeta,
)

@Entity(tableName = "ai_conversations", indices = [Index("user_id")])
data class AiConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val title: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @Embedded val sync: SyncMeta,
)

@Entity(
    tableName = "ai_messages",
    foreignKeys = [ForeignKey(AiConversationEntity::class, ["id"], ["conversation_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["conversation_id", "timestamp"])],
)
data class AiMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: AiRole,
    val content: String,
    /** Screen the message was sent from (e.g. "progress/exercise:bench_press"), for context. */
    @ColumnInfo(name = "screen_context") val screenContext: String?,
    val timestamp: Instant,
)
