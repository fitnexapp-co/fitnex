package com.fitnex.feature.auth.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.AccountManager
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.model.ActivityLevel
import com.fitnex.core.model.AgePolicy
import com.fitnex.core.model.AgeProfile
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExperienceLevel
import com.fitnex.core.model.ProfileLimits
import com.fitnex.core.model.UserProfile
import com.fitnex.feature.auth.validation.FieldError
import com.fitnex.feature.auth.validation.FormRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { ABOUT, TRAINING, EQUIPMENT, FOOD, PRIVACY }

/** Stable keys stored in the profile; labels come from string resources. */
object DietOptions {
    val PREFERENCES = listOf("omnivore", "vegetarian", "vegan", "pescatarian", "gluten_free", "lactose_free", "halal", "kosher")
    val ALLERGIES = listOf("nuts", "peanut", "milk", "egg", "gluten", "soy", "fish", "shellfish", "sesame")
}

data class OnboardingForm(
    val name: String = "",
    val birthDigits: String = "",
    val height: String = "",
    val weight: String = "",
    val experience: ExperienceLevel? = null,
    val activity: ActivityLevel? = null,
    val days: Set<DayOfWeek> = emptySet(),
    val sessionMinutes: Int = 45,
    val equipment: Set<Equipment> = emptySet(),
    val diet: Set<String> = emptySet(),
    val allergies: List<String> = emptyList(),
    val customAllergy: String = "",
    val aiConsent: Boolean = false,
)

data class OnboardingUiState(
    val loading: Boolean = true,
    val editMode: Boolean = false,
    val step: OnboardingStep = OnboardingStep.ABOUT,
    val form: OnboardingForm = OnboardingForm(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Signed in (e.g. from another device) but under the minimum age: cannot continue. */
    val blockedByAge: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val accounts: AccountManager,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var userId: String? = null
    private var existing: UserProfile? = null
    val today: LocalDate get() = LocalDate.now(clock)

    init {
        viewModelScope.launch {
            val uid = session.accountUserId.filterNotNull().first()
            userId = uid
            val profile = profiles.get(uid)
            existing = profile
            _state.update {
                it.copy(
                    loading = false,
                    editMode = profile?.hasCompletedOnboarding == true,
                    form = profile?.toForm() ?: OnboardingForm(),
                )
            }
        }
    }

    fun update(transform: (OnboardingForm) -> OnboardingForm) = _state.update { it.copy(form = transform(it.form)) }

    fun addCustomAllergy() = update { f ->
        val value = f.customAllergy.trim()
        if (value.isEmpty() || f.allergies.any { it.equals(value, ignoreCase = true) }) f.copy(customAllergy = "")
        else f.copy(allergies = f.allergies + value, customAllergy = "")
    }

    fun errorsFor(step: OnboardingStep, form: OnboardingForm = _state.value.form): Map<String, FieldError> = buildMap {
        if (step == OnboardingStep.ABOUT) {
            FormRules.name(form.name)?.let { put(FIELD_NAME, it) }
            FormRules.birthDate(form.birthDigits, today)?.let { put(FIELD_BIRTH, it) }
            FormRules.optionalDecimal(form.height, ProfileLimits.HEIGHT_CM)?.let { put(FIELD_HEIGHT, it) }
            FormRules.optionalDecimal(form.weight, ProfileLimits.WEIGHT_KG)?.let { put(FIELD_WEIGHT, it) }
        }
    }

    fun ageProfile(): AgeProfile = AgePolicy.profileFor(FormRules.parseDate(_state.value.form.birthDigits), today)

    fun next() {
        val s = _state.value
        val errors = errorsFor(s.step)
        if (errors[FIELD_BIRTH] == FieldError.TOO_YOUNG) {
            _state.update { it.copy(blockedByAge = true) }
            return
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val nextStep = OnboardingStep.entries.getOrNull(s.step.ordinal + 1)
        if (nextStep == null) finish() else _state.update { it.copy(step = nextStep, showErrors = false) }
    }

    /** Optional steps can be skipped; the "about" step holds required data and cannot. */
    fun skip() {
        if (_state.value.step == OnboardingStep.ABOUT) return
        next()
    }

    fun back() = _state.update { s ->
        OnboardingStep.entries.getOrNull(s.step.ordinal - 1)?.let { s.copy(step = it, showErrors = false) } ?: s
    }

    fun signOut() = viewModelScope.launch { accounts.signOut() }

    private fun finish() {
        val uid = userId ?: return
        val f = _state.value.form
        val now = clock.instant()
        val profile = UserProfile(
            id = uid,
            name = f.name.trim(),
            email = existing?.email,
            birthDate = FormRules.parseDate(f.birthDigits),
            heightCm = FormRules.parseDecimal(f.height),
            weightKg = FormRules.parseDecimal(f.weight),
            experience = f.experience,
            activityLevel = f.activity,
            availableDays = f.days,
            sessionMinutes = f.sessionMinutes.coerceIn(ProfileLimits.SESSION_MINUTES),
            equipment = f.equipment,
            dietaryPreferences = f.diet.toList().sorted(),
            allergies = f.allergies,
            aiConsentAt = if (f.aiConsent) existing?.aiConsentAt ?: now else null,
            createdAt = existing?.createdAt ?: now,
            onboardingCompletedAt = existing?.onboardingCompletedAt,
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            profiles.saveAndCompleteOnboarding(profile)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_BIRTH = "birth"
        const val FIELD_HEIGHT = "height"
        const val FIELD_WEIGHT = "weight"
    }
}

private fun UserProfile.toForm() = OnboardingForm(
    name = name,
    birthDigits = birthDate?.let(FormRules::formatDigits).orEmpty(),
    height = heightCm?.let(::formatNumber).orEmpty(),
    weight = weightKg?.let(::formatNumber).orEmpty(),
    experience = experience,
    activity = activityLevel,
    days = availableDays,
    sessionMinutes = sessionMinutes ?: 45,
    equipment = equipment,
    diet = dietaryPreferences.toSet(),
    allergies = allergies,
    aiConsent = hasAiConsent,
)

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString().replace('.', ',')
