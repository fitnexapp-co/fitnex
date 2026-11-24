package com.fitnex.core.data.nutrition

import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.database.dao.NutritionDao
import com.fitnex.core.database.entity.FoodItemEntity
import com.fitnex.core.database.entity.MealEntity
import com.fitnex.core.database.entity.RecipeEntity
import com.fitnex.core.database.entity.RecipeItemEntity
import com.fitnex.core.database.relation.RecipeWithItems
import com.fitnex.core.model.Recipe
import com.fitnex.core.model.RecipeItem
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.WaterLogEntity
import com.fitnex.core.database.relation.MealWithItems
import com.fitnex.core.model.AnalysisStatus
import com.fitnex.core.model.Food
import com.fitnex.core.model.FoodEntry
import com.fitnex.core.model.Meal
import com.fitnex.core.model.MealType
import com.fitnex.core.model.Nutrients
import com.fitnex.core.model.NutritionDay
import com.fitnex.core.model.NutritionMath
import com.fitnex.core.model.SyncStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** A water entry, so the diary can undo the last one. */
data class WaterEntry(val id: String, val ml: Int, val at: Instant)

/** Spec §6 "Diario nutricional": meals by type and day, foods with their nutrients, hydration. */
interface NutritionRepository {
    fun observeDay(userId: String, date: LocalDate): Flow<NutritionDay>
    fun observeRange(userId: String, from: LocalDate, to: LocalDate): Flow<List<Meal>>
    fun observeWater(userId: String, date: LocalDate): Flow<List<WaterEntry>>
    fun observeRecentFoodIds(userId: String, limit: Int = 12): Flow<List<String>>

    /** Logs [grams] of a catalog food in the day's meal of [type] (created on first use). */
    suspend fun addFood(userId: String, date: LocalDate, type: MealType, food: Food, grams: Float, portion: String): String

    /** A food not in the catalog, with the nutrients the user typed (all estimates). */
    suspend fun addCustom(userId: String, date: LocalDate, type: MealType, name: String, grams: Float?, nutrients: Nutrients): String

    /** New portion for a catalog food entry; nutrients are recomputed from the catalog. */
    suspend fun updatePortion(entryId: String, food: Food, grams: Float, portion: String)
    suspend fun deleteEntry(entryId: String)

    /** A food estimated from a photo (spec §6): values are estimates, [confidence] 0..1 is kept with it. */
    suspend fun addEstimated(userId: String, date: LocalDate, type: MealType, name: String, grams: Float, portion: String, nutrients: Nutrients, confidence: Float): String

    /** Keeps a photo with the meal (the automatic analysis arrives with FITNEX AI, phase 8). */
    suspend fun attachPhoto(userId: String, date: LocalDate, type: MealType, imageReference: String): String

    suspend fun addWater(userId: String, ml: Int): String
    suspend fun deleteWater(id: String)

    /** Water per day in [from]..[to] (inclusive), for the weekly summary. */
    fun observeWaterByDay(userId: String, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>

    /** Logs [servings] of a recipe as one entry with its per-serving nutrients. */
    suspend fun addRecipe(userId: String, date: LocalDate, type: MealType, recipe: Recipe, perServing: Nutrients, servings: Float, portion: String): String

    fun observeRecipes(userId: String): Flow<List<Recipe>>
    suspend fun getRecipe(id: String): Recipe?
    suspend fun saveRecipe(userId: String, recipe: Recipe)
    suspend fun deleteRecipe(id: String)
}

@Singleton
class RoomNutritionRepository @Inject constructor(
    private val dao: NutritionDao,
    private val ids: IdGenerator,
    private val clock: Clock,
) : NutritionRepository {

    override fun observeDay(userId: String, date: LocalDate): Flow<NutritionDay> =
        combine(dao.observeMeals(userId, date, date), observeWater(userId, date)) { meals, water ->
            NutritionDay(date, meals.map { it.toModel() }, water.sumOf { it.ml })
        }

    override fun observeRange(userId: String, from: LocalDate, to: LocalDate): Flow<List<Meal>> =
        dao.observeMeals(userId, from, to).map { rows -> rows.map { it.toModel() } }

    override fun observeWater(userId: String, date: LocalDate): Flow<List<WaterEntry>> {
        val zone = clock.zone
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant()
        return dao.observeWaterLogs(userId, from, to).map { rows -> rows.map { WaterEntry(it.id, it.quantityMl, it.timestamp) } }
    }

    override fun observeRecentFoodIds(userId: String, limit: Int) = dao.observeRecentFoodIds(userId, limit)

    override suspend fun addFood(userId: String, date: LocalDate, type: MealType, food: Food, grams: Float, portion: String): String {
        val meal = mealFor(userId, date, type)
        val id = ids.newId()
        dao.saveMeal(meal, listOf(item(id, meal.id, food.id, food.name, portion, grams, NutritionMath.forGrams(food, grams), edited = false)))
        return id
    }

    override suspend fun addCustom(userId: String, date: LocalDate, type: MealType, name: String, grams: Float?, nutrients: Nutrients): String {
        val meal = mealFor(userId, date, type)
        val id = ids.newId()
        val portion = grams?.let { "${it.toInt()} g" } ?: "1 ración"
        dao.saveMeal(meal, listOf(item(id, meal.id, null, name.trim(), portion, grams, nutrients, edited = true)))
        return id
    }

    override suspend fun addEstimated(userId: String, date: LocalDate, type: MealType, name: String, grams: Float, portion: String, nutrients: Nutrients, confidence: Float): String {
        val meal = mealFor(userId, date, type).copy(analysisStatus = AnalysisStatus.COMPLETED)
        val id = ids.newId()
        dao.saveMeal(meal, listOf(item(id, meal.id, null, name, portion, grams, nutrients, edited = false, confidence = confidence)))
        return id
    }

    override suspend fun updatePortion(entryId: String, food: Food, grams: Float, portion: String) {
        val old = dao.getFoodItem(entryId) ?: return
        dao.upsertFoodItems(listOf(item(old.id, old.mealId, food.id, food.name, portion, grams, NutritionMath.forGrams(food, grams), edited = old.userEdited)))
        touchMeal(old.mealId)
    }

    override suspend fun deleteEntry(entryId: String) {
        val old = dao.getFoodItem(entryId) ?: return
        val now = clock.instant()
        dao.upsertFoodItems(listOf(old.copy(sync = SyncMeta(now, SyncStatus.PENDING_DELETE, deletedAt = now))))
        touchMeal(old.mealId)
    }

    override suspend fun attachPhoto(userId: String, date: LocalDate, type: MealType, imageReference: String): String {
        val meal = mealFor(userId, date, type).copy(imageReference = imageReference, analysisStatus = AnalysisStatus.NOT_REQUESTED)
        dao.upsertMeal(meal)
        return meal.id
    }

    override suspend fun addWater(userId: String, ml: Int): String {
        val id = ids.newId()
        val now = clock.instant()
        dao.upsertWater(WaterLogEntity(id, userId, ml, now, SyncMeta(now)))
        return id
    }

    override suspend fun deleteWater(id: String) {
        val old = dao.getWater(id) ?: return
        val now = clock.instant()
        dao.upsertWater(old.copy(sync = SyncMeta(now, SyncStatus.PENDING_DELETE, deletedAt = now)))
    }

    override fun observeWaterByDay(userId: String, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> {
        val zone = clock.zone
        return dao.observeWaterLogs(userId, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
            .map { rows -> rows.groupBy { it.timestamp.atZone(zone).toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.quantityMl } } }
    }

    override suspend fun addRecipe(userId: String, date: LocalDate, type: MealType, recipe: Recipe, perServing: Nutrients, servings: Float, portion: String): String {
        val meal = mealFor(userId, date, type)
        val id = ids.newId()
        dao.saveMeal(meal, listOf(item(id, meal.id, null, recipe.name, portion, null, perServing.scaled(servings), edited = false)))
        return id
    }

    override fun observeRecipes(userId: String): Flow<List<Recipe>> = dao.observeRecipes(userId).map { rows -> rows.map { it.toModel() } }

    override suspend fun getRecipe(id: String): Recipe? = dao.getRecipe(id)?.takeIf { it.recipe.sync.deletedAt == null }?.toModel()

    override suspend fun saveRecipe(userId: String, recipe: Recipe) {
        val now = clock.instant()
        val createdAt = dao.getRecipe(recipe.id)?.recipe?.createdAt ?: now
        dao.saveRecipe(
            RecipeEntity(recipe.id, userId, recipe.name.trim(), recipe.servings, createdAt, SyncMeta(now)),
            recipe.items.mapIndexed { i, it -> RecipeItemEntity(ids.newId(), recipe.id, it.foodId, it.grams, i) },
        )
    }

    override suspend fun deleteRecipe(id: String) {
        val old = dao.getRecipe(id) ?: return
        val now = clock.instant()
        dao.upsertRecipe(old.recipe.copy(sync = SyncMeta(now, SyncStatus.PENDING_DELETE, deletedAt = now)))
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun mealFor(userId: String, date: LocalDate, type: MealType): MealEntity {
        val now = clock.instant()
        return dao.findMeal(userId, date, type)?.copy(sync = SyncMeta(now))
            ?: MealEntity(ids.newId(), userId, type, date, now, null, null, AnalysisStatus.MANUAL, SyncMeta(now))
    }

    private suspend fun touchMeal(mealId: String) {
        dao.getMeal(mealId)?.let { dao.upsertMeal(it.copy(sync = SyncMeta(clock.instant()))) }
    }

    private fun item(id: String, mealId: String, foodId: String?, name: String, portion: String, grams: Float?, n: Nutrients, edited: Boolean, confidence: Float? = null) =
        FoodItemEntity(
            id = id, mealId = mealId, foodId = foodId, name = name, portionDescription = portion, portionGrams = grams,
            proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG, fiberG = n.fiberG, kcalEstimate = n.kcal,
            micronutrientsJson = json.encodeToString(n.micros()), confidence = confidence, userEdited = edited,
            sync = SyncMeta(clock.instant()),
        )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun MealWithItems.toModel() = Meal(
            id = meal.id, type = meal.mealType, date = meal.date, loggedAt = meal.loggedAt,
            items = items.filter { it.sync.deletedAt == null }.map { it.toModel() },
            imageReference = meal.imageReference, analysisStatus = meal.analysisStatus,
        )

        fun RecipeWithItems.toModel() = Recipe(recipe.id, recipe.name, recipe.servings, items.sortedBy { it.position }.map { RecipeItem(it.foodId, it.grams) })

        fun FoodItemEntity.toModel(): FoodEntry {
            val micros = micronutrientsJson?.let { runCatching { json.decodeFromString<Map<String, Float>>(it) }.getOrNull() }.orEmpty()
            val n = Nutrients.fromMap(
                micros + mapOf(
                    "kcal" to (kcalEstimate ?: 0f), "protein_g" to (proteinG ?: 0f), "carbs_g" to (carbsG ?: 0f),
                    "fat_g" to (fatG ?: 0f), "fiber_g" to (fiberG ?: 0f),
                ),
            )
            return FoodEntry(id, mealId, foodId, name, portionDescription, portionGrams, n, confidence, userEdited)
        }
    }
}
