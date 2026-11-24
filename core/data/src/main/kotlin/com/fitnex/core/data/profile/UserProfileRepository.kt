package com.fitnex.core.data.profile

import com.fitnex.core.database.dao.AccountDao
import com.fitnex.core.database.dao.UserDao
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.UserEntity
import com.fitnex.core.model.SyncStatus
import com.fitnex.core.model.UserProfile
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserProfileRepository {
    fun observe(userId: String): Flow<UserProfile?>
    suspend fun get(userId: String): UserProfile?

    /** Creates the row right after sign-up with what the sign-up form collected. */
    suspend fun createAtSignUp(userId: String, email: String?, birthDate: LocalDate)

    /** Saves edits and marks onboarding complete if it was not already. */
    suspend fun saveAndCompleteOnboarding(profile: UserProfile)

    suspend fun setAiConsent(userId: String, granted: Boolean)

    /** Deletes every local row owned by [userId] (spec §11). */
    suspend fun deleteAllData(userId: String)
}

@Singleton
class RoomUserProfileRepository @Inject constructor(
    private val userDao: UserDao,
    private val accountDao: AccountDao,
    private val clock: Clock,
) : UserProfileRepository {

    override fun observe(userId: String): Flow<UserProfile?> = userDao.observe(userId).map { it?.toModel() }

    override suspend fun get(userId: String): UserProfile? = userDao.get(userId)?.takeIf { it.sync.deletedAt == null }?.toModel()

    override suspend fun createAtSignUp(userId: String, email: String?, birthDate: LocalDate) {
        if (userDao.get(userId) != null) return // idempotent: a retried sign-up must not reset data
        val now = clock.instant()
        userDao.upsert(
            UserEntity(
                id = userId, name = "", email = email, birthDate = birthDate, heightCm = null,
                optionalWeightKg = null, experience = null, activityLevel = null, availableDays = emptySet(),
                sessionMinutes = null, equipment = emptySet(), dietaryPreferences = emptyList(),
                allergies = emptyList(), aiConsentAt = null, createdAt = now, onboardingCompletedAt = null,
                sync = SyncMeta(updatedAt = now),
            ),
        )
    }

    override suspend fun saveAndCompleteOnboarding(profile: UserProfile) {
        val now = clock.instant()
        val existing = userDao.get(profile.id)
        userDao.upsert(
            profile.toEntity(
                createdAt = existing?.createdAt ?: profile.createdAt,
                onboardingCompletedAt = existing?.onboardingCompletedAt ?: now,
                sync = SyncMeta(updatedAt = now, syncStatus = SyncStatus.PENDING_UPSERT),
            ),
        )
    }

    override suspend fun setAiConsent(userId: String, granted: Boolean) {
        val entity = userDao.get(userId) ?: return
        val now = clock.instant()
        userDao.upsert(
            entity.copy(
                aiConsentAt = if (granted) entity.aiConsentAt ?: now else null,
                sync = SyncMeta(updatedAt = now, syncStatus = SyncStatus.PENDING_UPSERT),
            ),
        )
    }

    override suspend fun deleteAllData(userId: String) {
        // An account takes its "Entrenar juntos" profiles with it.
        if (com.fitnex.core.database.sync.PROFILE_SEPARATOR !in userId) {
            userDao.companionIds(userId + com.fitnex.core.database.sync.PROFILE_SEPARATOR).forEach { accountDao.deleteAllDataOf(it) }
        }
        accountDao.deleteAllDataOf(userId)
    }
}

internal fun UserEntity.toModel() = UserProfile(
    id = id, name = name, email = email, birthDate = birthDate, heightCm = heightCm,
    weightKg = optionalWeightKg, experience = experience, activityLevel = activityLevel,
    availableDays = availableDays, sessionMinutes = sessionMinutes, equipment = equipment,
    dietaryPreferences = dietaryPreferences, allergies = allergies, aiConsentAt = aiConsentAt,
    createdAt = createdAt, onboardingCompletedAt = onboardingCompletedAt,
)

internal fun UserProfile.toEntity(createdAt: java.time.Instant, onboardingCompletedAt: java.time.Instant?, sync: SyncMeta) =
    UserEntity(
        id = id, name = name.trim(), email = email, birthDate = birthDate, heightCm = heightCm,
        optionalWeightKg = weightKg, experience = experience, activityLevel = activityLevel,
        availableDays = availableDays, sessionMinutes = sessionMinutes, equipment = equipment,
        dietaryPreferences = dietaryPreferences, allergies = allergies, aiConsentAt = aiConsentAt,
        createdAt = createdAt, onboardingCompletedAt = onboardingCompletedAt, sync = sync,
    )
