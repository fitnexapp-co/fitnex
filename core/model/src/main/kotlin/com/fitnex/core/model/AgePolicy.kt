package com.fitnex.core.model

import java.time.LocalDate
import java.time.Period

/**
 * Central rule that adapts FITNEX to the user's age (spec §4, §6).
 * Every feature that could show calorie targets, deficits or weight-loss goals MUST consult this.
 */
object AgePolicy {
    const val ADULT_AGE = 18

    /**
     * Minimum age to create an account: 14, Spain's age of digital consent (LOPDGDD art. 7).
     * Chosen by the product owner; change here only together with the privacy policy.
     */
    const val MIN_REGISTRATION_AGE = 14

    /** Oldest plausible birth date accepted by forms (guards against typos like year 1026). */
    const val MAX_AGE = 120

    fun canRegister(birthDate: LocalDate, today: LocalDate): Boolean =
        !birthDate.isAfter(today) && ageOn(birthDate, today) in MIN_REGISTRATION_AGE..MAX_AGE

    fun ageOn(birthDate: LocalDate, today: LocalDate): Int {
        require(!birthDate.isAfter(today)) { "birthDate cannot be in the future" }
        return Period.between(birthDate, today).years
    }

    fun profileFor(birthDate: LocalDate?, today: LocalDate): AgeProfile {
        // Unknown age is treated conservatively, exactly like a minor.
        if (birthDate == null) return AgeProfile.UNKNOWN
        return if (ageOn(birthDate, today) >= ADULT_AGE) AgeProfile.ADULT else AgeProfile.MINOR
    }
}

enum class AgeProfile(
    /** Calorie numbers may be shown as estimates (never as restrictive goals). */
    val showCalorieEstimates: Boolean,
    /** Deficit / weight-loss targets are allowed. */
    val allowWeightLossGoals: Boolean,
) {
    ADULT(showCalorieEstimates = true, allowWeightLossGoals = true),
    MINOR(showCalorieEstimates = false, allowWeightLossGoals = false),
    UNKNOWN(showCalorieEstimates = false, allowWeightLossGoals = false),
}
