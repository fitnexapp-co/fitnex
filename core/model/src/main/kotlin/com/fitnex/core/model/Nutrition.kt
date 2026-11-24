package com.fitnex.core.model

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate

enum class FoodCategory { FRUIT, VEGETABLE, LEGUME, NUT, CEREAL, BREAD, DAIRY, EGG, POULTRY, MEAT, FISH, SHELLFISH, FAT, SWEET, DRINK, SNACK, DISH, SAUCE }

/**
 * Nutrient amounts. For catalog foods they come from USDA FoodData Central (per 100 g); for logged
 * food they are scaled to the portion. Values from a photo or a portion guess are ESTIMATES (spec §6).
 */
data class Nutrients(
    val kcal: Float = 0f,
    val proteinG: Float = 0f,
    val carbsG: Float = 0f,
    val fatG: Float = 0f,
    val fiberG: Float = 0f,
    val sugarsG: Float = 0f,
    val satFatG: Float = 0f,
    val sodiumMg: Float = 0f,
    val potassiumMg: Float = 0f,
    val calciumMg: Float = 0f,
    val ironMg: Float = 0f,
    val magnesiumMg: Float = 0f,
    val zincMg: Float = 0f,
    val vitaminAUg: Float = 0f,
    val vitaminCMg: Float = 0f,
    val vitaminDUg: Float = 0f,
    val vitaminB12Ug: Float = 0f,
    val folateUg: Float = 0f,
) {
    operator fun plus(o: Nutrients) = Nutrients(
        kcal + o.kcal, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG, fiberG + o.fiberG, sugarsG + o.sugarsG,
        satFatG + o.satFatG, sodiumMg + o.sodiumMg, potassiumMg + o.potassiumMg, calciumMg + o.calciumMg, ironMg + o.ironMg,
        magnesiumMg + o.magnesiumMg, zincMg + o.zincMg, vitaminAUg + o.vitaminAUg, vitaminCMg + o.vitaminCMg,
        vitaminDUg + o.vitaminDUg, vitaminB12Ug + o.vitaminB12Ug, folateUg + o.folateUg,
    )

    fun scaled(f: Float) = Nutrients(
        kcal * f, proteinG * f, carbsG * f, fatG * f, fiberG * f, sugarsG * f, satFatG * f, sodiumMg * f, potassiumMg * f,
        calciumMg * f, ironMg * f, magnesiumMg * f, zincMg * f, vitaminAUg * f, vitaminCMg * f, vitaminDUg * f,
        vitaminB12Ug * f, folateUg * f,
    )

    /** Everything except the five main values, keyed as in the catalog JSON (stored as micronutrients_json). */
    fun micros(): Map<String, Float> = mapOf(
        "sugars_g" to sugarsG, "sat_fat_g" to satFatG, "sodium_mg" to sodiumMg, "potassium_mg" to potassiumMg,
        "calcium_mg" to calciumMg, "iron_mg" to ironMg, "magnesium_mg" to magnesiumMg, "zinc_mg" to zincMg,
        "vitamin_a_ug" to vitaminAUg, "vitamin_c_mg" to vitaminCMg, "vitamin_d_ug" to vitaminDUg,
        "vitamin_b12_ug" to vitaminB12Ug, "folate_ug" to folateUg,
    )

    companion object {
        val ZERO = Nutrients()

        /** From catalog keys (kcal, protein_g, …); missing keys are 0. */
        fun fromMap(m: Map<String, Float>) = Nutrients(
            m["kcal"] ?: 0f, m["protein_g"] ?: 0f, m["carbs_g"] ?: 0f, m["fat_g"] ?: 0f, m["fiber_g"] ?: 0f,
            m["sugars_g"] ?: 0f, m["sat_fat_g"] ?: 0f, m["sodium_mg"] ?: 0f, m["potassium_mg"] ?: 0f, m["calcium_mg"] ?: 0f,
            m["iron_mg"] ?: 0f, m["magnesium_mg"] ?: 0f, m["zinc_mg"] ?: 0f, m["vitamin_a_ug"] ?: 0f, m["vitamin_c_mg"] ?: 0f,
            m["vitamin_d_ug"] ?: 0f, m["vitamin_b12_ug"] ?: 0f, m["folate_ug"] ?: 0f,
        )
    }
}

/** A catalog food. [allergens] use the profile's keys (nuts, peanut, milk, egg, gluten, soy, fish, shellfish, sesame). */
data class Food(
    val id: String,
    val name: String,
    val category: FoodCategory,
    val allergens: Set<String>,
    /** A household measure ("unidad mediana", "taza"…) and its weight, when one is typical. */
    val unitName: String?,
    val unitGrams: Float?,
    val per100g: Nutrients,
    val fdcId: Int,
)

/** One logged food. [foodId] links to the catalog; null for custom foods or photo results. */
data class FoodEntry(
    val id: String,
    val mealId: String,
    val foodId: String?,
    val name: String,
    val portion: String,
    val grams: Float?,
    val nutrients: Nutrients,
    /** 0..1 from a photo analysis; null for manual entries. */
    val confidence: Float?,
    val userEdited: Boolean,
)

data class Meal(
    val id: String,
    val type: MealType,
    val date: LocalDate,
    val loggedAt: Instant,
    val items: List<FoodEntry>,
    /** Local photo of the meal, when the user added one. */
    val imageReference: String?,
    val analysisStatus: AnalysisStatus,
)

data class NutritionDay(
    val date: LocalDate,
    val meals: List<Meal>,
    val waterMl: Int,
) {
    val totals: Nutrients get() = meals.flatMap { it.items }.fold(Nutrients.ZERO) { acc, e -> acc + e.nutrients }
    /** Different foods eaten: catalog id when known, otherwise the name. */
    val distinctFoods: Int get() = meals.flatMap { it.items }.map { it.foodId ?: it.name.lowercase() }.toSet().size
    val hasEntries: Boolean get() = meals.any { it.items.isNotEmpty() } || waterMl > 0
    fun meal(type: MealType): Meal? = meals.firstOrNull { it.type == type }
}

object NutritionMath {
    fun forGrams(food: Food, grams: Float): Nutrients = food.per100g.scaled(grams / 100f)

    /** Allergens of [food] that the user declared. */
    fun conflicts(food: Food, userAllergies: Collection<String>): Set<String> = food.allergens.intersect(userAllergies.toSet())

    /** Case- and accent-insensitive search key ("Plátano" → "platano"). */
    fun searchKey(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().trim()

    /** Catalog search: every typed word must appear; names starting with the query first. */
    fun search(foods: List<Food>, query: String, category: FoodCategory? = null): List<Food> {
        val q = searchKey(query)
        val words = q.split(' ').filter { it.isNotBlank() }
        return foods
            .filter { category == null || it.category == category }
            .filter { f -> val key = searchKey(f.name); words.all { it in key } }
            .sortedWith(compareByDescending<Food> { searchKey(it.name).startsWith(q) }.thenBy { it.name })
    }
}

object NutritionLimits {
    const val MAX_GRAMS = 2_000f
    val WATER_QUICK_ML = listOf(250, 500)
    const val MAX_WATER_ML = 2_000
}
