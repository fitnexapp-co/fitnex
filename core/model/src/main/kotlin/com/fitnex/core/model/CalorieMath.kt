package com.fitnex.core.model

import kotlin.math.roundToInt

/** Sex used only by the calorie calculator (Mifflin-St Jeor needs it); never stored. */
enum class CalculatorSex { MALE, FEMALE }

/** What the adult wants from their daily target. Small, sustainable changes only. */
enum class CalorieAim(val factor: Float) { LOSE(0.85f), MAINTAIN(1f), GAIN(1.10f) }

/** Daily macro targets in grams, derived from a calorie target. */
data class MacroTargets(val proteinG: Int, val carbsG: Int, val fatG: Int)

/**
 * Daily calorie estimate for ADULTS (Mifflin-St Jeor × activity). It is an estimate to orient,
 * not a prescription; minors never see it (AgePolicy.showCalorieEstimates).
 */
object CalorieMath {
    val GOAL_RANGE = 1200..5000

    private fun activityFactor(level: ActivityLevel?): Float = when (level) {
        ActivityLevel.SEDENTARY, null -> 1.2f
        ActivityLevel.LIGHT -> 1.375f
        ActivityLevel.MODERATE -> 1.55f
        ActivityLevel.ACTIVE -> 1.725f
        ActivityLevel.VERY_ACTIVE -> 1.9f
    }

    /** Resting energy (kcal/day). */
    fun bmr(sex: CalculatorSex, ageYears: Int, weightKg: Float, heightCm: Float): Float =
        10f * weightKg + 6.25f * heightCm - 5f * ageYears + if (sex == CalculatorSex.MALE) 5f else -161f

    /** Daily target rounded to 10 kcal and kept inside [GOAL_RANGE]. */
    fun dailyTarget(sex: CalculatorSex, ageYears: Int, weightKg: Float, heightCm: Float, activity: ActivityLevel?, aim: CalorieAim): Int {
        val kcal = bmr(sex, ageYears, weightKg, heightCm) * activityFactor(activity) * aim.factor
        return ((kcal / 10f).roundToInt() * 10).coerceIn(GOAL_RANGE)
    }

    /** Balanced split: 25 % protein, 45 % carbohydrates, 30 % fat (4 / 4 / 9 kcal per gram). */
    fun macros(kcal: Int): MacroTargets = MacroTargets(
        proteinG = (kcal * 0.25f / 4f).roundToInt(),
        carbsG = (kcal * 0.45f / 4f).roundToInt(),
        fatG = (kcal * 0.30f / 9f).roundToInt(),
    )
}
