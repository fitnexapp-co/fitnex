package com.fitnex.core.data.profile

import com.fitnex.core.common.time.IdGenerator
import com.fitnex.core.data.session.ActiveProfileSource
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.database.dao.AccountDao
import com.fitnex.core.database.dao.UserDao
import com.fitnex.core.database.entity.SyncMeta
import com.fitnex.core.database.entity.UserEntity
import com.fitnex.core.database.sync.PROFILE_SEPARATOR
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.ProfileLimits
import com.fitnex.core.model.UserProfile
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** What "Entrenar juntos" asks for a new profile: nothing else, and no account of its own. */
data class TrainingProfileDraft(val name: String, val age: Int, val heightCm: Float?, val weightKg: Float?)

object TrainingProfileRules {
    /** Same minimum as the app's own sign-up; minors (14-17) get the protected experience. */
    val AGE = 14..100
    const val MAX_PROFILES = 8

    fun isCompanion(id: String) = PROFILE_SEPARATOR in id
    fun accountOf(id: String) = id.substringBefore(PROFILE_SEPARATOR)
}

/**
 * The remembered choice is only honoured while that profile still exists on this device;
 * otherwise the owner's data is shown.
 */
@Singleton
class StoredActiveProfile @Inject constructor(
    private val prefs: UserPreferencesDataSource,
    private val userDao: UserDao,
) : ActiveProfileSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun active(accountId: String): Flow<String?> = prefs.activeProfile(accountId).flatMapLatest { id ->
        if (id == null || !id.startsWith(accountId + PROFILE_SEPARATOR)) flowOf(null)
        else userDao.observe(id).map { if (it == null) null else id }
    }
}

/**
 * "Entrenar juntos": extra people trained from this account (friends, family). Each one is a
 * profile with its own workouts, progress and diary, switched from the account menu.
 */
@Singleton
class TrainingProfilesRepository @Inject constructor(
    private val session: SessionRepository,
    private val userDao: UserDao,
    private val accountDao: AccountDao,
    private val prefs: UserPreferencesDataSource,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    /** The owner first, then the profiles in creation order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profiles: Flow<List<UserProfile>> = session.accountUserId.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else combine(userDao.observe(uid), userDao.observeCompanions(uid + PROFILE_SEPARATOR)) { owner, others ->
            listOfNotNull(owner?.toModel()) + others.map { it.toModel() }
        }
    }

    val activeId: Flow<String?> get() = session.currentUserId

    /** Creates the profile and returns its id; the caller decides whether to switch to it. */
    suspend fun create(draft: TrainingProfileDraft): String {
        val uid = session.accountUserId.first() ?: error("Not signed in")
        require(draft.name.isNotBlank() && draft.name.trim().length <= ProfileLimits.NAME_MAX_LENGTH) { "Invalid name" }
        require(draft.age in TrainingProfileRules.AGE) { "Invalid age" }
        val owner = userDao.get(uid)
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val adult = draft.age >= 18
        val id = uid + PROFILE_SEPARATOR + ids.newId().replace("-", "").take(12)
        userDao.upsert(
            UserEntity(
                id = id,
                name = draft.name.trim(),
                email = null,
                // Halfway through that year of age, so the age shown is the one typed.
                birthDate = today.minusYears(draft.age.toLong()).minusMonths(6),
                heightCm = draft.heightCm?.takeIf { it in ProfileLimits.HEIGHT_CM },
                // Minors are never asked for, nor shown, a body weight.
                optionalWeightKg = if (adult) draft.weightKg?.takeIf { it in ProfileLimits.WEIGHT_KG } else null,
                experience = null,
                activityLevel = null,
                // Trained together: start from the owner's days, time and equipment.
                availableDays = owner?.availableDays.orEmpty(),
                sessionMinutes = owner?.sessionMinutes,
                equipment = owner?.equipment.orEmpty(),
                dietaryPreferences = emptyList(),
                allergies = emptyList(),
                aiConsentAt = null,
                createdAt = now,
                onboardingCompletedAt = now,
                sync = SyncMeta(updatedAt = now, syncStatus = com.fitnex.core.model.SyncStatus.PENDING_UPSERT),
            ),
        )
        return id
    }

    suspend fun switchTo(profileId: String) {
        val uid = session.accountUserId.first() ?: return
        if (profileId == uid || profileId.startsWith(uid + PROFILE_SEPARATOR)) prefs.setActiveProfile(uid, profileId)
    }

    /** Deletes a profile and everything it logged; the owner's data is never touched. */
    suspend fun delete(profileId: String) {
        val uid = session.accountUserId.first() ?: return
        if (!profileId.startsWith(uid + PROFILE_SEPARATOR)) return
        if (session.currentUserId.first() == profileId) prefs.setActiveProfile(uid, null)
        accountDao.deleteProfileData(profileId)
    }
}
