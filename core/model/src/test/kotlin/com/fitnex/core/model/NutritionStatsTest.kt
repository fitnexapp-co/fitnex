package com.fitnex.core.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionStatsTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun food(id: String, cat: FoodCategory, protein: Float, allergens: Set<String> = emptySet()) =
        Food(id, id, cat, allergens, null, null, Nutrients(proteinG = protein, fiberG = 1f), 0)
    private val foods = listOf(food("oats", FoodCategory.CEREAL, 13f, setOf("gluten")), food("milk", FoodCategory.DAIRY, 3f, setOf("milk")), food("banana", FoodCategory.FRUIT, 1f))
        .associateBy { it.id }

    private fun meal(date: LocalDate, type: MealType, vararg items: Pair<String?, Float>) = Meal(
        "${date}_$type", type, date, Instant.EPOCH,
        items.mapIndexed { i, (foodId, protein) -> FoodEntry("$date$type$i", "m", foodId, foodId ?: "Tarta", "1", 100f, Nutrients(proteinG = protein, fiberG = 2f), null, false) },
        null, AnalysisStatus.MANUAL,
    )

    @Test
    fun weekAveragesOnlyLoggedDaysAndReportsHabits() {
        val meals = listOf(
            meal(today, MealType.BREAKFAST, "oats" to 10f, "banana" to 1f),
            meal(today, MealType.LUNCH, null to 9f),
            meal(today.minusDays(2), MealType.BREAKFAST, "oats" to 10f),
            meal(today.minusDays(9), MealType.DINNER, "milk" to 30f), // outside the week
            meal(today.minusDays(3), MealType.SNACK), // empty meal: not a logged day
        )
        val w = NutritionStatsCalculator.week(today, meals, mapOf(today to 1000, today.minusDays(5) to 500), foods)
        assertEquals(7, w.days.size)
        assertEquals(today, w.days.last().date)
        assertEquals("2 days with food + 1 with only water", 3, w.loggedDays.size)
        assertEquals(20f, w.days.last().nutrients.proteinG)
        assertEquals((20f + 10f + 0f) / 3, w.averagePerLoggedDay { it.nutrients.proteinG }!!, 0.001f)
        assertEquals(3, w.distinctFoods) // oats, banana, "tarta"
        assertEquals(FoodFrequency("oats", 2), w.topFoods.first())
        assertEquals(2, w.daysWithBreakfast)
        assertEquals(setOf(FoodCategory.CEREAL, FoodCategory.FRUIT), w.categoriesEaten)
    }

    @Test
    fun emptyWeekHasNoAverages() {
        val w = NutritionStatsCalculator.week(today, emptyList(), emptyMap(), foods)
        assertTrue(w.loggedDays.isEmpty())
        assertNull(w.averagePerLoggedDay { it.nutrients.proteinG })
    }

    @Test
    fun recipeIsSplitIntoServingsAndKnowsItsAllergens() {
        val r = Recipe("r", "Gachas", servings = 2, items = listOf(RecipeItem("oats", 80f), RecipeItem("milk", 250f), RecipeItem("gone", 50f)))
        val per = RecipeMath.perServing(r, foods)
        assertEquals((13f * 0.8f + 3f * 2.5f) / 2, per.proteinG, 0.001f)
        assertEquals("unknown ingredients are skipped, not guessed", (0.8f + 2.5f) / 2, per.fiberG, 0.001f)
        assertEquals(setOf("gluten", "milk"), RecipeMath.allergens(r, foods))
    }
}
