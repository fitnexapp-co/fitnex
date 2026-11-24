package com.fitnex.core.testing

import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.model.UserProfile
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory profile store with the same semantics as the Room implementation. */
class FakeUserProfileRepository(
    private val now: () -> Instant = { Instant.parse("2026-11-19T10:00:00Z") },
) : UserProfileRepository {
    private val store = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    override fun observe(userId: String): Flow<UserProfile?> = store.map { it[userId] }
    override suspend fun get(userId: String): UserProfile? = store.value[userId]

    override suspend fun createAtSignUp(userId: String, email: String?, birthDate: LocalDate) {
        if (userId in store.value) return
        store.update { it + (userId to emptyProfile(userId, email, birthDate)) }
    }

    override suspend fun saveAndCompleteOnboarding(profile: UserProfile) {
        val existing = store.value[profile.id]
        store.update {
            it + (profile.id to profile.copy(
                createdAt = existing?.createdAt ?: profile.createdAt,
                onboardingCompletedAt = existing?.onboardingCompletedAt ?: now(),
            ))
        }
    }

    override suspend fun setAiConsent(userId: String, granted: Boolean) {
        store.update { map ->
            val p = map[userId] ?: return@update map
            map + (userId to p.copy(aiConsentAt = if (granted) p.aiConsentAt ?: now() else null))
        }
    }

    override suspend fun deleteAllData(userId: String) = store.update { it - userId }

    fun emptyProfile(userId: String, email: String?, birthDate: LocalDate?) = UserProfile(
        id = userId, name = "", email = email, birthDate = birthDate, heightCm = null, weightKg = null,
        experience = null, activityLevel = null, availableDays = emptySet(), sessionMinutes = null,
        equipment = emptySet(), dietaryPreferences = emptyList(), allergies = emptyList(),
        aiConsentAt = null, createdAt = now(), onboardingCompletedAt = null,
    )
}
