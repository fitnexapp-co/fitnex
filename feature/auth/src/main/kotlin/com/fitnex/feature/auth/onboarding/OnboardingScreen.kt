package com.fitnex.feature.auth.onboarding

import com.fitnex.core.designsystem.component.fitnexScreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.fitnex.core.designsystem.component.FitnexChipGroup
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.picker.FitnexDatePickerField
import com.fitnex.core.designsystem.component.picker.FitnexNumberPickerField
import com.fitnex.core.model.ProfileLimits
import com.fitnex.feature.auth.validation.FormRules
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.FitnexSliderRow
import com.fitnex.core.designsystem.component.FitnexSwitchRow
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.ActivityLevel
import com.fitnex.core.model.AgeProfile
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExperienceLevel
import com.fitnex.feature.auth.R
import com.fitnex.feature.auth.onboarding.OnboardingViewModel.Companion.FIELD_BIRTH
import com.fitnex.feature.auth.onboarding.OnboardingViewModel.Companion.FIELD_HEIGHT
import com.fitnex.feature.auth.onboarding.OnboardingViewModel.Companion.FIELD_NAME
import com.fitnex.feature.auth.onboarding.OnboardingViewModel.Companion.FIELD_WEIGHT
import com.fitnex.feature.auth.ui.MIN_AGE
import com.fitnex.feature.auth.ui.messageRes
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.serialization.Serializable

/** First-time setup, shown by the app while the session is "needs onboarding". */
@Composable
fun OnboardingFlow() = OnboardingScreen(onDone = {}, onBack = null)

@Serializable
data object EditProfileRoute

/** Same form, reachable from Settings once onboarding is complete. */
fun NavGraphBuilder.editProfileScreen(onDone: () -> Unit) {
    fitnexScreen<EditProfileRoute> { OnboardingScreen(onDone = onDone, onBack = onDone) }
}

@Composable
internal fun OnboardingScreen(
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved, s.editMode) { if (s.saved && s.editMode) onDone() }
    val colors = FitnexTheme.colors

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (s.loading) {
            LoadingState()
            return@Column
        }
        if (s.blockedByAge) {
            BlockedByAge(onSignOut = viewModel::signOut)
            return@Column
        }
        val steps = OnboardingStep.entries.size
        if (s.editMode) {
            FitnexHeader(stringResource(R.string.onb_edit_title), onBack = onBack)
        } else {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg)) {
                Text(
                    stringResource(R.string.onb_step, s.step.ordinal + 1, steps),
                    style = FitnexTheme.typography.labelMedium, color = colors.accent,
                )
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { (s.step.ordinal + 1f) / steps },
                    color = colors.accent, trackColor = colors.textDisabled.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val reduceMotion = FitnexTheme.appearance.reduceMotion
        AnimatedContent(
            targetState = s.step,
            // "Reducir animaciones": a short fade only, no sideways movement.
            transitionSpec = {
                if (reduceMotion) fadeIn(androidx.compose.animation.core.tween(120)) togetherWith fadeOut(androidx.compose.animation.core.tween(90))
                else (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut()
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val errors = if (s.showErrors) viewModel.errorsFor(step, s.form) else emptyMap()
                when (step) {
                    OnboardingStep.ABOUT -> AboutStep(s.form, errors, viewModel.ageProfile(), viewModel::update)
                    OnboardingStep.TRAINING -> TrainingStep(s.form, viewModel::update)
                    OnboardingStep.EQUIPMENT -> EquipmentStep(s.form, viewModel::update)
                    OnboardingStep.FOOD -> FoodStep(s.form, viewModel::update, viewModel::addCustomAllergy)
                    OnboardingStep.PRIVACY -> PrivacyStep(s.form, viewModel::update)
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }

        // Bottom actions
        val isLast = s.step == OnboardingStep.entries.last()
        Row(
            Modifier.fillMaxWidth().padding(Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (s.step != OnboardingStep.ABOUT) {
                FitnexSecondaryButton(stringResource(R.string.onb_back), viewModel::back, Modifier.weight(1f).testTag("onb_back"))
            }
            if (s.step != OnboardingStep.ABOUT && !isLast) {
                TextButton(onClick = viewModel::skip, modifier = Modifier.testTag("onb_skip")) {
                    Text(stringResource(R.string.onb_skip), color = colors.textSecondary, style = FitnexTheme.typography.labelLarge)
                }
            }
            val primary = when {
                !isLast -> R.string.onb_next
                s.editMode -> R.string.onb_save
                else -> R.string.onb_finish
            }
            FitnexPrimaryButton(stringResource(primary), viewModel::next, Modifier.weight(1.4f).testTag("onb_next"), loading = s.saving)
        }
    }
}

@Composable
private fun StepIntro(title: Int, body: Int) {
    Text(stringResource(title), style = FitnexTheme.typography.headlineLarge, color = FitnexTheme.colors.textPrimary)
    Text(stringResource(body), style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.textSecondary)
}

@Composable
private fun AboutStep(
    form: OnboardingForm,
    errors: Map<String, com.fitnex.feature.auth.validation.FieldError>,
    ageProfile: AgeProfile,
    update: ((OnboardingForm) -> OnboardingForm) -> Unit,
) {
    StepIntro(R.string.onb_about_title, R.string.onb_about_body)
    FitnexTextField(
        value = form.name, onValueChange = { v -> update { it.copy(name = v) } }, label = stringResource(R.string.onb_name),
        supportingText = stringResource(R.string.onb_name_why),
        errorText = errors[FIELD_NAME]?.let { stringResource(it.messageRes()) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.testTag("onb_name"),
    )
    FitnexDatePickerField(
        value = FormRules.parseDate(form.birthDigits),
        onValueChange = { d -> update { it.copy(birthDigits = FormRules.formatDigits(d)) } },
        label = stringResource(R.string.signup_birthdate),
        supportingText = stringResource(R.string.onb_birthdate_why),
        errorText = errors[FIELD_BIRTH]?.let { stringResource(it.messageRes()) },
        modifier = Modifier.testTag("onb_birth"),
    )
    if (ageProfile == AgeProfile.MINOR) {
        GlassCard(Modifier.testTag("onb_minor_note")) {
            Text(stringResource(R.string.onb_minor_note), style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.textPrimary)
        }
    }
    // Wheels instead of the keyboard: values are always in range and never mistyped.
    FitnexNumberPickerField(
        value = FormRules.parseDecimal(form.height),
        onValueChange = { v -> update { it.copy(height = v?.toInt()?.toString().orEmpty()) } },
        label = stringResource(R.string.onb_height) + " · " + stringResource(R.string.onb_optional),
        unit = "cm",
        range = ProfileLimits.HEIGHT_CM.start.toInt()..ProfileLimits.HEIGHT_CM.endInclusive.toInt(),
        defaultValue = 170f,
        supportingText = stringResource(R.string.onb_height_why),
        errorText = errors[FIELD_HEIGHT]?.let { stringResource(it.messageRes()) },
        modifier = Modifier.testTag("onb_height"),
    )
    FitnexNumberPickerField(
        value = FormRules.parseDecimal(form.weight),
        onValueChange = { v -> update { it.copy(weight = v?.let(::weightText).orEmpty()) } },
        label = stringResource(R.string.onb_weight) + " · " + stringResource(R.string.onb_optional),
        unit = "kg",
        range = ProfileLimits.WEIGHT_KG.start.toInt()..ProfileLimits.WEIGHT_KG.endInclusive.toInt(),
        defaultValue = 70f,
        withDecimal = true,
        supportingText = stringResource(R.string.onb_weight_why),
        errorText = errors[FIELD_WEIGHT]?.let { stringResource(it.messageRes()) },
        modifier = Modifier.testTag("onb_weight"),
    )
}

@Composable
private fun TrainingStep(form: OnboardingForm, update: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val locale = rememberAppLocale()
    StepIntro(R.string.onb_training_title, R.string.onb_training_body)
    GlassCard {
        SectionHeader(stringResource(R.string.onb_experience))
        val labels = mapOf(
            ExperienceLevel.BEGINNER to stringResource(R.string.onb_exp_beginner),
            ExperienceLevel.INTERMEDIATE to stringResource(R.string.onb_exp_intermediate),
            ExperienceLevel.ADVANCED to stringResource(R.string.onb_exp_advanced),
        )
        FitnexChipGroup(
            options = ExperienceLevel.entries, isSelected = { it == form.experience },
            onToggle = { v -> update { it.copy(experience = if (it.experience == v) null else v) } },
            label = { labels.getValue(it) },
        )
    }
    GlassCard {
        SectionHeader(stringResource(R.string.onb_activity))
        val labels = mapOf(
            ActivityLevel.SEDENTARY to stringResource(R.string.onb_act_sedentary),
            ActivityLevel.LIGHT to stringResource(R.string.onb_act_light),
            ActivityLevel.MODERATE to stringResource(R.string.onb_act_moderate),
            ActivityLevel.ACTIVE to stringResource(R.string.onb_act_active),
            ActivityLevel.VERY_ACTIVE to stringResource(R.string.onb_act_very_active),
        )
        FitnexChipGroup(
            options = ActivityLevel.entries, isSelected = { it == form.activity },
            onToggle = { v -> update { it.copy(activity = if (it.activity == v) null else v) } },
            label = { labels.getValue(it) },
        )
    }
    GlassCard {
        SectionHeader(stringResource(R.string.onb_days))
        FitnexChipGroup(
            options = DayOfWeek.entries, isSelected = { it in form.days },
            onToggle = { d -> update { it.copy(days = if (d in it.days) it.days - d else it.days + d) } },
            label = { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { c -> c.titlecase(locale) } },
        )
        FitnexSliderRow(
            title = stringResource(R.string.onb_session_minutes),
            value = form.sessionMinutes.toFloat(),
            onValueChange = { v -> update { it.copy(sessionMinutes = (v / 5).toInt() * 5) } },
            valueRange = 15f..180f, steps = 0,
            valueLabel = stringResource(R.string.onb_minutes, form.sessionMinutes),
        )
    }
}

@Composable
private fun EquipmentStep(form: OnboardingForm, update: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    StepIntro(R.string.onb_equipment_title, R.string.onb_equipment_body)
    val labels = mapOf(
        Equipment.BODYWEIGHT to R.string.eq_bodyweight, Equipment.DUMBBELL to R.string.eq_dumbbell,
        Equipment.BARBELL to R.string.eq_barbell, Equipment.KETTLEBELL to R.string.eq_kettlebell,
        Equipment.MACHINE to R.string.eq_machine, Equipment.CABLE to R.string.eq_cable,
        Equipment.RESISTANCE_BAND to R.string.eq_band, Equipment.BENCH to R.string.eq_bench,
        Equipment.PULL_UP_BAR to R.string.eq_pullup_bar, Equipment.OTHER to R.string.eq_other,
        Equipment.EZ_BAR to R.string.eq_ez_bar, Equipment.DIP_BARS to R.string.eq_dip_bars,
        Equipment.RACK to R.string.eq_rack, Equipment.MEDICINE_BALL to R.string.eq_medicine_ball,
        Equipment.ROPE to R.string.eq_rope, Equipment.SLED to R.string.eq_sled,
        Equipment.SANDBAG to R.string.eq_sandbag, Equipment.CARDIO_MACHINE to R.string.eq_cardio_machine,
    ).mapValues { stringResource(it.value) }
    FitnexChipGroup(
        options = Equipment.entries, isSelected = { it in form.equipment },
        onToggle = { e -> update { it.copy(equipment = if (e in it.equipment) it.equipment - e else it.equipment + e) } },
        label = { labels.getValue(it) },
    )
}

@Composable
private fun FoodStep(
    form: OnboardingForm,
    update: ((OnboardingForm) -> OnboardingForm) -> Unit,
    addCustomAllergy: () -> Unit,
) {
    StepIntro(R.string.onb_food_title, R.string.onb_food_body)
    val dietLabels = DietOptions.PREFERENCES.associateWith { stringResource(dietRes(it)) }
    val allergyLabels = DietOptions.ALLERGIES.associateWith { stringResource(allergyRes(it)) }
    GlassCard {
        SectionHeader(stringResource(R.string.onb_diet))
        FitnexChipGroup(
            options = DietOptions.PREFERENCES, isSelected = { it in form.diet },
            onToggle = { d -> update { it.copy(diet = if (d in it.diet) it.diet - d else it.diet + d) } },
            label = { dietLabels.getValue(it) },
        )
    }
    GlassCard {
        SectionHeader(stringResource(R.string.onb_allergies))
        // Known keys first, then any custom allergy the user typed.
        val options = DietOptions.ALLERGIES + form.allergies.filterNot { it in DietOptions.ALLERGIES }
        FitnexChipGroup(
            options = options, isSelected = { it in form.allergies },
            onToggle = { a -> update { it.copy(allergies = if (a in it.allergies) it.allergies - a else it.allergies + a) } },
            label = { allergyLabels[it] ?: it },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            FitnexTextField(
                value = form.customAllergy, onValueChange = { v -> update { it.copy(customAllergy = v) } },
                label = stringResource(R.string.onb_allergy_other), modifier = Modifier.weight(1f),
            )
            TextButton(onClick = addCustomAllergy, enabled = form.customAllergy.isNotBlank()) {
                Text(stringResource(R.string.onb_allergy_add), color = FitnexTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun PrivacyStep(form: OnboardingForm, update: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val colors = FitnexTheme.colors
    StepIntro(R.string.onb_privacy_title, R.string.onb_privacy_body)
    GlassCard {
        FitnexSwitchRow(
            title = stringResource(R.string.onb_ai_consent),
            description = stringResource(R.string.onb_ai_consent_desc),
            checked = form.aiConsent,
            onCheckedChange = { v -> update { it.copy(aiConsent = v) } },
            modifier = Modifier.testTag("onb_ai_consent"),
        )
        if (!form.aiConsent) {
            Text(stringResource(R.string.onb_ai_off_note), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
    Text(stringResource(R.string.onb_disclaimer), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
}

@Composable
private fun BlockedByAge(onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Spacing.gutter), verticalArrangement = Arrangement.Center) {
        GlassCard(Modifier.testTag("onb_blocked")) {
            Text(stringResource(R.string.onb_blocked_title), style = FitnexTheme.typography.titleLarge, color = FitnexTheme.colors.textPrimary)
            Text(stringResource(R.string.onb_blocked_body, MIN_AGE), style = FitnexTheme.typography.bodyMedium, color = FitnexTheme.colors.textSecondary)
            Spacer(Modifier.height(Spacing.md))
            FitnexPrimaryButton(stringResource(R.string.onb_sign_out), onSignOut, Modifier.fillMaxWidth())
        }
    }
}

/** 72.5 → "72,5", 70.0 → "70" (the form stores what FormRules.parseDecimal reads back). */
private fun weightText(kg: Float): String =
    if (kg % 1f == 0f) kg.toInt().toString() else "%d,%d".format(kg.toInt(), Math.round((kg - kg.toInt()) * 10))

private fun dietRes(key: String) = when (key) {
    "omnivore" -> R.string.diet_omnivore
    "vegetarian" -> R.string.diet_vegetarian
    "vegan" -> R.string.diet_vegan
    "pescatarian" -> R.string.diet_pescatarian
    "gluten_free" -> R.string.diet_gluten_free
    "lactose_free" -> R.string.diet_lactose_free
    "halal" -> R.string.diet_halal
    else -> R.string.diet_kosher
}

private fun allergyRes(key: String) = when (key) {
    "nuts" -> R.string.allergy_nuts
    "peanut" -> R.string.allergy_peanut
    "milk" -> R.string.allergy_milk
    "egg" -> R.string.allergy_egg
    "gluten" -> R.string.allergy_gluten
    "soy" -> R.string.allergy_soy
    "fish" -> R.string.allergy_fish
    "shellfish" -> R.string.allergy_shellfish
    else -> R.string.allergy_sesame
}
