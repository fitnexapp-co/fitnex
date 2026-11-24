package com.fitnex.core.model

import java.time.LocalDate

/** A user recipe: catalog ingredients for the whole dish, eaten in [servings] equal parts. */
data class Recipe(
    val id: String,
    val name: String,
    val servings: Int,
    val items: List<RecipeItem>,
)

data class RecipeItem(val foodId: String, val grams: Float)

object RecipeMath {
    /** Whole dish; ingredients missing from the catalog are skipped (never guessed). */
    fun total(recipe: Recipe, foods: Map<String, Food>): Nutrients =
        recipe.items.fold(Nutrients.ZERO) { acc, i -> foods[i.foodId]?.let { acc + NutritionMath.forGrams(it, i.grams) } ?: acc }

    fun perServing(recipe: Recipe, foods: Map<String, Food>): Nutrients =
        total(recipe, foods).scaled(1f / recipe.servings.coerceAtLeast(1))

    fun allergens(recipe: Recipe, foods: Map<String, Food>): Set<String> =
        recipe.items.flatMap { foods[it.foodId]?.allergens.orEmpty() }.toSet()

    val SERVINGS = 1..12
    const val MAX_ITEMS = 30
}

data class NutritionDayStat(
    val date: LocalDate,
    val nutrients: Nutrients,
    val waterMl: Int,
    val mealsLogged: Int,
    val distinctFoods: Int,
) {
    val logged: Boolean get() = mealsLogged > 0 || waterMl > 0
}

data class FoodFrequency(val name: String, val times: Int)

/**
 * Spec §20 "Estadísticas de nutrición": history, variety, nutrients registered, hydration and
 * habits — descriptive only. No targets, no scores, no comparisons (spec §6 and §20).
 */
data class NutritionWeek(
    val days: List<NutritionDayStat>,
    val distinctFoods: Int,
    val topFoods: List<FoodFrequency>,
    val categoriesEaten: Set<FoodCategory>,
    val daysWithBreakfast: Int,
) {
    val loggedDays: List<NutritionDayStat> get() = days.filter { it.logged }

    /** Average over the days with something logged (empty days are not zeros). */
    fun averagePerLoggedDay(pick: (NutritionDayStat) -> Float): Float? =
        loggedDays.takeIf { it.isNotEmpty() }?.let { d -> d.map(pick).average().toFloat() }
}

object NutritionStatsCalculator {
    fun week(
        endInclusive: LocalDate,
        meals: List<Meal>,
        waterByDay: Map<LocalDate, Int>,
        foods: Map<String, Food>,
        days: Int = 7,
    ): NutritionWeek {
        val start = endInclusive.minusDays((days - 1).toLong())
        val inRange = meals.filter { !it.date.isBefore(start) && !it.date.isAfter(endInclusive) }
        val stats = (0 until days).map { i ->
            val d = start.plusDays(i.toLong())
            val dayMeals = inRange.filter { it.date == d && it.items.isNotEmpty() }
            val items = dayMeals.flatMap { it.items }
            NutritionDayStat(
                date = d,
                nutrients = items.fold(Nutrients.ZERO) { a, e -> a + e.nutrients },
                waterMl = waterByDay[d] ?: 0,
                mealsLogged = dayMeals.size,
                distinctFoods = items.map { it.foodId ?: it.name.lowercase() }.toSet().size,
            )
        }
        val allItems = inRange.flatMap { it.items }
        return NutritionWeek(
            days = stats,
            distinctFoods = allItems.map { it.foodId ?: it.name.lowercase() }.toSet().size,
            topFoods = allItems.groupBy { it.foodId ?: it.name.lowercase() }
                .map { (_, list) -> FoodFrequency(list.first().name, list.size) }
                .sortedWith(compareByDescending<FoodFrequency> { it.times }.thenBy { it.name })
                .take(5),
            categoriesEaten = allItems.mapNotNull { e -> e.foodId?.let { foods[it]?.category } }.toSet(),
            daysWithBreakfast = inRange.filter { it.type == MealType.BREAKFAST && it.items.isNotEmpty() }.map { it.date }.toSet().size,
        )
    }
}
