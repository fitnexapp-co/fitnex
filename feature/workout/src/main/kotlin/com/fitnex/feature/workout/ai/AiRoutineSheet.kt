package com.fitnex.feature.workout.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnex.core.data.ai.AiResult
import com.fitnex.core.data.ai.AiRoutineBuilder
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AiFailure
import com.fitnex.core.model.RoutineGoal
import com.fitnex.core.model.RoutineProposal
import com.fitnex.core.model.RoutineRequest
import com.fitnex.feature.workout.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiRoutineUiState(
    /** False = one session; true = a weekly plan with one routine per training day. */
    val weekly: Boolean = false,
    val plan: com.fitnex.core.model.RoutinePlanProposal? = null,
    val goal: RoutineGoal = RoutineGoal.GENERAL,
    val minutes: Int = 45,
    val focus: String = "",
    val loading: Boolean = false,
    val proposal: RoutineProposal? = null,
    val error: AiFailure? = null,
    val saving: Boolean = false,
    val savedId: String? = null,
)

@HiltViewModel
class AiRoutineViewModel @Inject constructor(
    private val session: SessionRepository,
    private val profiles: UserProfileRepository,
    private val builder: AiRoutineBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow(AiRoutineUiState())
    val state: StateFlow<AiRoutineUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            profiles.get(uid)?.sessionMinutes?.let { m -> _state.update { it.copy(minutes = MINUTES.minBy { o -> kotlin.math.abs(o - m) }) } }
        }
    }

    fun setWeekly(weekly: Boolean) = _state.update { it.copy(weekly = weekly, proposal = null, plan = null, error = null) }
    fun setGoal(goal: RoutineGoal) = _state.update { it.copy(goal = goal) }
    fun setMinutes(minutes: Int) = _state.update { it.copy(minutes = minutes) }
    fun setFocus(text: String) = _state.update { it.copy(focus = text.take(300)) }

    fun generate() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null, proposal = null, plan = null) }
        viewModelScope.launch {
            val uid = session.currentUserId.first()
            val s = _state.value
            val request = RoutineRequest(s.goal, s.minutes, s.focus)
            if (uid == null) { _state.update { it.copy(loading = false, error = AiFailure.NOT_SIGNED_IN) }; return@launch }
            if (s.weekly) {
                val r = builder.proposePlan(uid, request)
                _state.update { if (r is AiResult.Ok) it.copy(loading = false, plan = r.value) else it.copy(loading = false, error = (r as AiResult.Failed).reason) }
            } else {
                val r = builder.propose(uid, request)
                _state.update { if (r is AiResult.Ok) it.copy(loading = false, proposal = r.value) else it.copy(loading = false, error = (r as AiResult.Failed).reason) }
            }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || (s.proposal == null && s.plan == null)) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val uid = session.currentUserId.first() ?: return@launch
            val id = s.plan?.let { builder.savePlan(uid, it).first() } ?: builder.save(uid, s.proposal!!)
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }

    companion object {
        val MINUTES = listOf(30, 45, 60, 90)
    }
}

/** "Crear con FITNEX AI": choose, generate, review, save — nothing is stored before "Guardar". */
@Composable
internal fun AiRoutineSheet(onDismiss: () -> Unit, onSaved: (String) -> Unit, vm: AiRoutineViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = FitnexTheme.colors
    LaunchedEffect(state.savedId) { state.savedId?.let { onSaved(it) } }

    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.ai_routine_title)) {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("ai_routine_sheet"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val proposal = state.proposal
            val plan = state.plan
            if (plan != null) {
                PlanPreview(plan, state.saving, vm::save, vm::generate)
            } else if (proposal == null) {
                val modes = mapOf(false to stringResource(R.string.ai_mode_single), true to stringResource(R.string.ai_mode_weekly))
                FitnexSegmentedControl(listOf(false, true), state.weekly, vm::setWeekly, { modes.getValue(it) }, Modifier.testTag("ai_mode"))
                Text(stringResource(if (state.weekly) R.string.ai_routine_intro_weekly else R.string.ai_routine_intro), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                Text(stringResource(R.string.ai_routine_goal), style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                val goals = mapOf(
                    RoutineGoal.STRENGTH to stringResource(R.string.ai_goal_strength),
                    RoutineGoal.HYPERTROPHY to stringResource(R.string.ai_goal_muscle),
                    RoutineGoal.ENDURANCE to stringResource(R.string.ai_goal_endurance),
                    RoutineGoal.GENERAL to stringResource(R.string.ai_goal_general),
                )
                FitnexSegmentedControl(RoutineGoal.entries, state.goal, vm::setGoal, { goals.getValue(it) }, Modifier.testTag("ai_goal"))
                Text(stringResource(R.string.ai_routine_minutes), style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                FitnexSegmentedControl(AiRoutineViewModel.MINUTES, state.minutes, vm::setMinutes, { "$it min" })
                FitnexTextField(
                    value = state.focus, onValueChange = vm::setFocus,
                    label = stringResource(R.string.ai_routine_focus), singleLine = false,
                    supportingText = stringResource(R.string.ai_routine_focus_hint),
                    modifier = Modifier.testTag("ai_focus"),
                )
                state.error?.let { Text(stringResource(it.messageRes()), style = FitnexTheme.typography.bodySmall, color = colors.error, modifier = Modifier.testTag("ai_routine_error")) }
                FitnexPrimaryButton(
                    stringResource(R.string.ai_routine_generate), vm::generate,
                    Modifier.fillMaxWidth().testTag("ai_generate"), loading = state.loading,
                )
            } else {
                Text(proposal.name, style = FitnexTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.testTag("ai_proposal_name"))
                if (proposal.rationale.isNotBlank()) {
                    Text(proposal.rationale, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                Column(Modifier.fillMaxWidth()) {
                    proposal.exercises.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = colors.divider)
                        Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = Spacing.sm)) {
                            Row {
                                Text("${i + 1}. ${e.exerciseName}", style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.ai_routine_sets, e.sets, e.reps), style = FitnexTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = colors.accent)
                            }
                            Text(
                                stringResource(R.string.ai_routine_rest, e.restSeconds) + if (e.note.isNotBlank()) " · ${e.note}" else "",
                                style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.ai_routine_disclaimer), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
                FitnexPrimaryButton(stringResource(R.string.ai_routine_save), vm::save, Modifier.fillMaxWidth().testTag("ai_save"), loading = state.saving)
                FitnexSecondaryButton(stringResource(R.string.ai_routine_again), vm::generate, Modifier.fillMaxWidth().testTag("ai_again"))
            }
        }
    }
}

/** The weekly plan, day by day, with one save for all of it. */
@Composable
private fun PlanPreview(plan: com.fitnex.core.model.RoutinePlanProposal, saving: Boolean, onSave: () -> Unit, onAgain: () -> Unit) {
    val colors = FitnexTheme.colors
    Text(plan.name, style = FitnexTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.testTag("ai_plan_name"))
    if (plan.rationale.isNotBlank()) Text(plan.rationale, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
    plan.sessions.forEachIndexed { d, s ->
        Text(s.name, style = FitnexTheme.typography.titleMedium, color = colors.accent, modifier = Modifier.padding(top = Spacing.sm))
        s.exercises.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                Text("${i + 1}. ${e.exerciseName}", style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.ai_routine_sets, e.sets, e.reps), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        if (d < plan.sessions.lastIndex) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(top = Spacing.sm))
    }
    Text(stringResource(R.string.ai_plan_days_note), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
    FitnexPrimaryButton(androidx.compose.ui.res.pluralStringResource(R.plurals.ai_plan_save, plan.sessions.size, plan.sessions.size), onSave, Modifier.fillMaxWidth().testTag("ai_plan_save"), loading = saving)
    FitnexSecondaryButton(stringResource(R.string.ai_routine_again), onAgain, Modifier.fillMaxWidth())
}

internal fun AiFailure.messageRes(): Int = when (this) {
    AiFailure.NO_CONSENT -> R.string.ai_err_consent
    AiFailure.NO_CONNECTION -> R.string.ai_err_offline
    AiFailure.RATE_LIMITED -> R.string.ai_err_limit
    AiFailure.NOT_CONFIGURED -> R.string.ai_err_config
    AiFailure.NOT_SIGNED_IN -> R.string.ai_err_session
    AiFailure.UNAVAILABLE -> R.string.ai_err_unavailable
}
