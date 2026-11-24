package com.fitnex.core.data.nutrition

import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.database.dao.NutritionDao
import com.fitnex.core.database.entity.MealPlanItemEntity
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.model.SyncStatus
import com.fitnex.core.model.Food
import com.fitnex.core.model.MealType
import com.fitnex.core.model.Recipe
import com.fitnex.core.model.RecipeMath
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One item of the weekly meal plan: a catalog food (grams) or a recipe (servings). */
data class PlannedItem(
    val id: String,
    val day: DayOfWeek,
    val mealType: MealType,
    val foodId: String?,
    val recipeId: String?,
    val name: String,
    val grams: Float?,
    val servings: Float?,
    val portion: String,
)

/**
 * Weekly meal plan (Nutrición › Plan de comidas). It repeats every week; "Añadir al diario" copies
 * one day's plan into the diary of a date, with nutrients computed from the catalog/recipe then.
 */
@Singleton
class MealPlanRepository @Inject constructor(
    private val dao: NutritionDao,
    private val nutrition: NutritionRepository,
    private val catalog: FoodCatalogRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun observe(userId: String): Flow<List<PlannedItem>> = dao.observePlan(userId).map { rows -> rows.map { it.toModel() } }

    suspend fun addFood(userId: String, day: DayOfWeek, type: MealType, food: Food, grams: Float, portion: String): String =
        insert(userId, day, type, foodId = food.id, recipeId = null, name = food.name, grams = grams, servings = null, portion = portion)

    suspend fun addRecipe(userId: String, day: DayOfWeek, type: MealType, recipe: Recipe, servings: Float, portion: String): String =
        insert(userId, day, type, foodId = null, recipeId = recipe.id, name = recipe.name, grams = null, servings = servings, portion = portion)

    suspend fun remove(id: String) {
        val old = dao.getPlanItem(id) ?: return
        val now = clock.instant()
        dao.upsertPlanItem(old.copy(sync = SyncMeta(now, SyncStatus.PENDING_DELETE, deletedAt = now)))
    }

    /**
     * Logs every planned item of [day] in the diary of [date]. Items whose food or recipe no longer
     * exists are skipped. Returns how many entries were added.
     */
    suspend fun copyDayToDiary(userId: String, day: DayOfWeek, date: LocalDate): Int {
        val items = dao.planForDay(userId, day.value)
        if (items.isEmpty()) return 0
        val foods = catalog.all().associateBy { it.id }
        var added = 0
        items.forEach { item ->
            val food = item.foodId?.let(foods::get)
            val grams = item.grams
            if (food != null && grams != null) {
                nutrition.addFood(userId, date, item.mealType, food, grams, item.portion)
                added++
                return@forEach
            }
            val recipe = item.recipeId?.let { nutrition.getRecipe(it) }
            val servings = item.servings
            if (recipe != null && servings != null) {
                nutrition.addRecipe(userId, date, item.mealType, recipe, RecipeMath.perServing(recipe, foods), servings, item.portion)
                added++
            }
        }
        return added
    }

    private suspend fun insert(
        userId: String, day: DayOfWeek, type: MealType, foodId: String?, recipeId: String?,
        name: String, grams: Float?, servings: Float?, portion: String,
    ): String {
        val id = ids.newId()
        val now = clock.instant()
        dao.upsertPlanItem(
            MealPlanItemEntity(
                id = id, userId = userId, dayOfWeek = day.value, mealType = type, foodId = foodId, recipeId = recipeId,
                name = name, grams = grams, servings = servings, portion = portion,
                position = dao.nextPlanPosition(userId, day.value, type), createdAt = now, sync = SyncMeta(now),
            ),
        )
        return id
    }

    private fun MealPlanItemEntity.toModel() =
        PlannedItem(id, DayOfWeek.of(dayOfWeek), mealType, foodId, recipeId, name, grams, servings, portion)
}
