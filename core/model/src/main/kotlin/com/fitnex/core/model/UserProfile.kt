package com.fitnex.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * The user's profile (spec §4). Everything except [id], [name] and [birthDate] is optional so
 * onboarding can be skipped; null means "not provided", never a default guess.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val email: String?,
    val birthDate: LocalDate?,
    val heightCm: Float?,
    val weightKg: Float?,
    val experience: ExperienceLevel?,
    val activityLevel: ActivityLevel?,
    val availableDays: Set<DayOfWeek>,
    val sessionMinutes: Int?,
    val equipment: Set<Equipment>,
    val dietaryPreferences: List<String>,
    val allergies: List<String>,
    /** When the user consented to FITNEX AI processing their data; null = no consent (spec §11). */
    val aiConsentAt: Instant?,
    val createdAt: Instant,
    val onboardingCompletedAt: Instant?,
) {
    val hasCompletedOnboarding: Boolean get() = onboardingCompletedAt != null
    val hasAiConsent: Boolean get() = aiConsentAt != null

    fun ageProfile(today: LocalDate): AgeProfile = AgePolicy.profileFor(birthDate, today)
}

/** Physical limits used to validate onboarding input (rejects typos, not real people). */
object ProfileLimits {
    val HEIGHT_CM = 100f..250f
    val WEIGHT_KG = 25f..350f
    val SESSION_MINUTES = 15..180
    const val NAME_MAX_LENGTH = 40
}
