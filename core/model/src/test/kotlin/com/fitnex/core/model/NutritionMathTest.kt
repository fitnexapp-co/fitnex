package com.fitnex.core.model

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionMathTest {
    private fun food(id: String, name: String, cat: FoodCategory, allergens: Set<String> = emptySet(), protein: Float = 0f, kcal: Float = 0f) =
        Food(id, name, cat, allergens, null, null, Nutrients(kcal = kcal, proteinG = protein), 0)

    private val foods = listOf(
        food("banana", "Plátano", FoodCategory.FRUIT, kcal = 89f),
        food("chicken", "Pechuga de pollo a la plancha", FoodCategory.POULTRY, protein = 31f),
        food("chicken_raw", "Pechuga de pollo cruda", FoodCategory.POULTRY),
        food("yogurt", "Yogur griego natural", FoodCategory.DAIRY, allergens = setOf("milk")),
        food("peanut_butter", "Crema de cacahuete", FoodCategory.NUT, allergens = setOf("peanut")),
    )

    @Test
    fun searchIgnoresAccentsAndCaseAndNeedsEveryWord() {
        assertEquals(listOf("banana"), NutritionMath.search(foods, "PLATANO").map { it.id })
        assertEquals(listOf("chicken", "chicken_raw"), NutritionMath.search(foods, "pollo").map { it.id })
        assertEquals(listOf("chicken_raw"), NutritionMath.search(foods, "pollo cruda").map { it.id })
        assertEquals(foods.size, NutritionMath.search(foods, "  ").size)
    }

    @Test
    fun searchPutsPrefixMatchesFirstAndFiltersByCategory() {
        val all = foods + food("milkshake", "Batido de plátano", FoodCategory.DRINK)
        assertEquals(listOf("banana", "milkshake"), NutritionMath.search(all, "plat").map { it.id })
        assertEquals(listOf("milkshake"), NutritionMath.search(all, "plat", FoodCategory.DRINK).map { it.id })
    }

    @Test
    fun portionScalesPer100gValues() {
        val n = NutritionMath.forGrams(foods[1], 150f)
        assertEquals(46.5f, n.proteinG, 0.001f)
    }

    @Test
    fun allergenConflictsOnlyForDeclaredAllergies() {
        assertEquals(setOf("milk"), NutritionMath.conflicts(foods[3], listOf("milk", "egg")))
        assertTrue(NutritionMath.conflicts(foods[4], listOf("milk")).isEmpty())
    }

    @Test
    fun dayTotalsAndVarietyCountRealEntriesOnly() {
        fun entry(id: String, foodId: String?, name: String, protein: Float) =
            FoodEntry(id, "m", foodId, name, "1", 100f, Nutrients(proteinG = protein), null, false)
        val day = NutritionDay(
            LocalDate.of(2026, 11, 20),
            listOf(
                Meal("m1", MealType.BREAKFAST, LocalDate.of(2026, 11, 20), Instant.EPOCH, listOf(entry("a", "banana", "Plátano", 1f), entry("b", "banana", "Plátano", 1f)), null, AnalysisStatus.MANUAL),
                Meal("m2", MealType.LUNCH, LocalDate.of(2026, 11, 20), Instant.EPOCH, listOf(entry("c", null, "Tarta de la abuela", 4f)), null, AnalysisStatus.MANUAL),
            ),
            waterMl = 500,
        )
        assertEquals(6f, day.totals.proteinG)
        assertEquals("same food twice counts once", 2, day.distinctFoods)
        assertTrue(day.hasEntries)
        assertTrue(!NutritionDay(LocalDate.MIN, emptyList(), 0).hasEntries)
    }

    @Test
    fun microsRoundTripThroughTheStoredMap() {
        val n = Nutrients(kcal = 10f, proteinG = 1f, vitaminCMg = 8.7f, ironMg = 0.3f)
        assertEquals(n, Nutrients.fromMap(n.micros() + mapOf("kcal" to 10f, "protein_g" to 1f)))
    }
}
