package com.fitnex.feature.workout.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.training.CustomExerciseDraft
import com.fitnex.core.data.training.ExerciseRepository
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexChipGroup
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.Muscle
import com.fitnex.feature.workout.R
import com.fitnex.feature.workout.ui.label
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomExerciseForm(
    val name: String = "",
    val type: ExerciseType = ExerciseType.STRENGTH,
    val equipment: Set<Equipment> = emptySet(),
    val muscles: List<String> = emptyList(),
    /** Null = automatic from type and equipment (what most people want). */
    val trackingChoice: com.fitnex.core.model.Tracking? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val createdId: String? = null,
) {
    val nameError get() = showErrors && name.isBlank()
    val musclesError get() = showErrors && muscles.isEmpty()
    val tracking: com.fitnex.core.model.Tracking get() = trackingChoice ?: com.fitnex.core.model.TrackingRules.forCustom(type, equipment)
}

@HiltViewModel
class CustomExerciseViewModel @Inject constructor(
    private val session: SessionRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    val muscles: StateFlow<List<Muscle>> = exercises.observeMuscles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _form = MutableStateFlow(CustomExerciseForm())
    val form: StateFlow<CustomExerciseForm> = _form.asStateFlow()

    fun setName(v: String) = _form.update { it.copy(name = v.take(60)) }
    fun setType(t: ExerciseType) = _form.update { it.copy(type = t) }
    fun setTracking(t: com.fitnex.core.model.Tracking) = _form.update { it.copy(trackingChoice = t) }
    fun toggleEquipment(e: Equipment) = _form.update { it.copy(equipment = if (e in it.equipment) it.equipment - e else it.equipment + e) }
    /** Up to three main muscles; the first ones picked stay. */
    fun toggleMuscle(id: String) = _form.update {
        it.copy(muscles = if (id in it.muscles) it.muscles - id else (it.muscles + id).take(3))
    }

    fun save() {
        val f = _form.value
        if (f.name.isBlank() || f.muscles.isEmpty()) { _form.update { it.copy(showErrors = true) }; return }
        if (f.saving) return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            val id = exercises.createCustom(uid, CustomExerciseDraft(f.name, "", f.equipment, f.type, f.muscles, tracking = f.tracking))
            _form.update { it.copy(saving = false, createdId = id) }
        }
    }
}

/**
 * "Crear ejercicio": name, type, how it is logged, main muscles (grouped by body area) and
 * equipment, each in its own card. Only the name and one muscle are required.
 */
@Composable
internal fun CustomExerciseSheet(onDismiss: () -> Unit, onCreated: (String) -> Unit, vm: CustomExerciseViewModel = hiltViewModel()) {
    val form by vm.form.collectAsStateWithLifecycle()
    val muscles by vm.muscles.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    LaunchedEffect(form.createdId) { form.createdId?.let(onCreated) }

    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.exercise_custom_title)) {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("custom_exercise_sheet"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FitnexTextField(
                value = form.name, onValueChange = vm::setName, label = stringResource(R.string.exercise_custom_name),
                errorText = if (form.nameError) stringResource(R.string.exercise_custom_err_name) else null,
                modifier = Modifier.testTag("custom_name"),
            )

            FormCard(stringResource(R.string.exercise_custom_type)) {
                val typeLabels = ExerciseType.entries.associateWith { it.label() }
                FitnexChipGroup(ExerciseType.entries, { it == form.type }, vm::setType, { typeLabels.getValue(it) })
            }

            FormCard(stringResource(R.string.exercise_custom_tracking), hint = stringResource(R.string.exercise_custom_tracking_hint)) {
                val trackingLabels = com.fitnex.core.model.Tracking.entries.associateWith { it.label() }
                FitnexChipGroup(com.fitnex.core.model.Tracking.entries, { it == form.tracking }, vm::setTracking, { trackingLabels.getValue(it) }, Modifier.testTag("custom_tracking"))
            }

            FormCard(
                stringResource(R.string.exercise_custom_muscles),
                trailing = "${form.muscles.size}/3",
                error = if (form.musclesError) stringResource(R.string.exercise_custom_err_muscles) else null,
            ) {
                Column(Modifier.testTag("custom_muscles"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    muscles.groupBy { it.region }.toSortedMap().forEach { (region, list) ->
                        Text(region.label(), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
                        FitnexChipGroup(list, { it.id in form.muscles }, { vm.toggleMuscle(it.id) }, { it.name })
                    }
                }
            }

            FormCard(stringResource(R.string.exercise_custom_equipment)) {
                val equipmentLabels = Equipment.entries.associateWith { it.label() }
                FitnexChipGroup(Equipment.entries, { it in form.equipment }, vm::toggleEquipment, { equipmentLabels.getValue(it) })
            }

            FitnexPrimaryButton(stringResource(R.string.exercise_custom_save), vm::save, Modifier.fillMaxWidth().testTag("custom_save"), loading = form.saving)
        }
    }
}

@Composable
private fun FormCard(
    title: String,
    hint: String? = null,
    trailing: String? = null,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = FitnexTheme.colors
    com.fitnex.core.designsystem.component.GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (trailing != null) Text(trailing, style = FitnexTheme.typography.labelMedium, color = colors.accent)
        }
        if (hint != null) Text(hint, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
        if (error != null) Text(error, style = FitnexTheme.typography.bodySmall, color = colors.error)
        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.sm))
        content()
    }
}