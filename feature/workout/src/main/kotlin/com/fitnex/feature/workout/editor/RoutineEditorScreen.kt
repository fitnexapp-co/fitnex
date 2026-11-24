package com.fitnex.feature.workout.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.fitnex.core.designsystem.component.FitnexChipGroup
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexSwitchRow
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.component.picker.FitnexMultiWheelSheet
import com.fitnex.core.designsystem.component.picker.WheelColumn
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.RoutineExercise
import com.fitnex.core.model.RoutineLimits
import com.fitnex.feature.workout.R
import com.fitnex.feature.workout.ui.formatKg
import com.fitnex.feature.workout.ui.formatRest
import com.fitnex.feature.workout.ui.shortLabel
import java.time.DayOfWeek

/** Weight wheel: "—" (no target) then 0,5 … 300 kg in 0,5 kg steps. */
private val WEIGHT_OPTIONS: List<Float?> = listOf<Float?>(null) + (1..600).map { it * 0.5f }

@Composable
internal fun RoutineEditorScreen(
    state: RoutineEditorUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    /** Receives the localized " (copia)" suffix for the copy's name. */
    onDuplicate: (String) -> Unit,
    onAddExercises: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onConfigure: (index: Int, sets: Int, reps: Int, rest: Int, weight: Float?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = FitnexTheme.colors
    LaunchedEffect(state.finished) { if (state.finished) onBack() }
    var configuring by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        FitnexHeader(
            title = stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title),
            overline = stringResource(R.string.editor_overline),
            onBack = onBack,
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("editor_delete")) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.editor_delete), tint = colors.error)
                    }
                }
            },
        )
        if (state.loading) {
            LoadingState()
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            state.aiRationale?.let { why ->
                GlassCard(Modifier.testTag("editor_ai_rationale"), emphasis = com.fitnex.core.designsystem.component.CardEmphasis.Featured) {
                    SectionHeader(stringResource(R.string.ai_routine_why))
                    Text(why, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
            FitnexTextField(
                value = state.name, onValueChange = onName, label = stringResource(R.string.editor_name),
                errorText = if (state.nameError) stringResource(R.string.editor_err_name) else null,
                modifier = Modifier.testTag("editor_name"),
            )
            GlassCard {
                SectionHeader(stringResource(R.string.editor_days))
                val labels = DayOfWeek.entries.associateWith { it.shortLabel() }
                FitnexChipGroup(DayOfWeek.entries, { it in state.days }, onToggleDay, { labels.getValue(it) })
            }

            SectionHeader(stringResource(R.string.editor_exercises))
            if (state.items.size > 1) {
                Text(stringResource(R.string.editor_drag_hint), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
            state.items.forEachIndexed { index, item ->
                // Keyed by item: a card keeps its drag while it moves to its new position.
                androidx.compose.runtime.key(item.id) { RoutineItemCard(
                    item = item,
                    tracking = state.trackingOf(item.exerciseId),
                    mediaUri = state.media[item.exerciseId],
                    canMoveUp = index > 0,
                    canMoveDown = index < state.items.lastIndex,
                    onClick = { configuring = index },
                    onUp = { onMove(index, -1) },
                    onDown = { onMove(index, 1) },
                    onRemove = { onRemove(index) },
                ) }
            }
            if (state.exercisesError) {
                Text(stringResource(R.string.editor_no_exercises), color = colors.error, style = FitnexTheme.typography.bodyMedium)
            }
            if (state.limitReached) {
                Text(stringResource(R.string.editor_too_many, RoutineLimits.MAX_EXERCISES), color = colors.warning, style = FitnexTheme.typography.bodySmall)
            }
            if (state.items.size < RoutineLimits.MAX_EXERCISES) {
                FitnexSecondaryButton(
                    stringResource(R.string.editor_add_exercises), onAddExercises,
                    Modifier.fillMaxWidth().testTag("editor_add_exercises"),
                )
            }
            // A saved routine can be the starting point of another one: a copy with the same
            // exercises, no weekdays, to adjust and save under its own name.
            if (!state.isNew) {
                val suffix = stringResource(R.string.editor_copy_suffix)
                androidx.compose.material3.TextButton(
                    onClick = { onDuplicate(suffix) }, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("editor_duplicate"),
                ) { Text(stringResource(R.string.editor_duplicate), color = colors.accent, style = FitnexTheme.typography.labelLarge) }
                Text(stringResource(R.string.editor_duplicate_desc), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter)) {
            FitnexPrimaryButton(
                stringResource(R.string.editor_save), onSave,
                Modifier.fillMaxWidth().testTag("editor_save"), loading = state.saving,
            )
        }
    }

    configuring?.let { index ->
        val item = state.items.getOrNull(index)
        if (item == null) {
            configuring = null
        } else {
            ConfigureSheet(item = item, tracking = state.trackingOf(item.exerciseId), weighted = item.exerciseId in state.weighted, onDismiss = { configuring = null }) { sets, reps, rest, weight ->
                onConfigure(index, sets, reps, rest, weight)
                configuring = null
            }
        }
    }
    if (confirmDelete) {
        FitnexConfirmDialog(
            title = stringResource(R.string.editor_delete_title),
            message = stringResource(R.string.editor_delete_body),
            confirmLabel = stringResource(R.string.editor_delete_confirm),
            dismissLabel = stringResource(R.string.editor_cancel),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun RoutineItemCard(
    item: RoutineExercise,
    tracking: com.fitnex.core.model.Tracking = com.fitnex.core.model.Tracking.WEIGHT_REPS,
    mediaUri: String? = null,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = FitnexTheme.colors
    // Drag the handle up or down: every card height travelled moves the exercise one place.
    var dragY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val up by androidx.compose.runtime.rememberUpdatedState(onUp)
    val down by androidx.compose.runtime.rememberUpdatedState(onDown)
    val canUp by androidx.compose.runtime.rememberUpdatedState(canMoveUp)
    val canDown by androidx.compose.runtime.rememberUpdatedState(canMoveDown)
    var cardHeight by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(1) }
    val upLabel = stringResource(R.string.editor_move_up, item.exerciseName)
    val downLabel = stringResource(R.string.editor_move_down, item.exerciseName)
    GlassCard(
        onClick = onClick,
        onClickLabel = stringResource(R.string.editor_configure, item.exerciseName),
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged { cardHeight = it.height }
            .graphicsLayer { translationY = dragY; shadowElevation = if (dragY != 0f) 16f else 0f }
            .zIndex(if (dragY != 0f) 1f else 0f)
            .semantics {
                // TalkBack: "Subir / Bajar" as actions on the card instead of two extra buttons.
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(upLabel) { onUp(); true })
                    if (canMoveDown) add(CustomAccessibilityAction(downLabel) { onDown(); true })
                }
            }
            .testTag("item_${item.exerciseId}"),
        contentPadding = PaddingValues(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { dragY = 0f }, onDragCancel = { dragY = 0f },
                    ) { change, dy ->
                        change.consume()
                        dragY += dy
                        val step = cardHeight.toFloat().coerceAtLeast(1f)
                        if (dragY > step / 2 && canDown) { down(); dragY -= step }
                        else if (dragY < -step / 2 && canUp) { up(); dragY += step }
                    }
                }.testTag("drag_${item.exerciseId}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Menu, null, tint = colors.textSecondary)
            }
            com.fitnex.feature.workout.media.ExerciseThumbnail(mediaUri, Modifier.padding(end = Spacing.sm), size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(item.exerciseName, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary)
                val base = stringResource(R.string.editor_summary_any, item.sets, com.fitnex.feature.workout.ui.formatTarget(tracking, item.reps), formatRest(item.restSeconds))
                Text(
                    item.targetWeightKg?.let { stringResource(R.string.editor_summary_weight, base, formatKg(it)) } ?: base,
                    style = FitnexTheme.typography.bodySmall, color = colors.accent,
                    modifier = Modifier.testTag("summary_${item.exerciseId}"),
                )
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, stringResource(R.string.editor_remove, item.exerciseName), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ConfigureSheet(
    item: RoutineExercise,
    tracking: com.fitnex.core.model.Tracking,
    weighted: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (sets: Int, reps: Int, rest: Int, weight: Float?) -> Unit,
) {
    val sets = RoutineLimits.SETS.toList()
    // The target wheel means reps, seconds (timed holds, stretches) or minutes (cardio).
    val targets = RoutineLimits.targetOptions(tracking)
    val rests = RoutineLimits.REST_SECONDS
    val current = RoutineLimits.clampTarget(tracking, item.reps)
    val columns = remember(item, tracking, weighted) {
        buildList {
            add(WheelColumn("", sets.map(Int::toString), sets.indexOf(item.sets).coerceAtLeast(0), 0.8f, "wheel_sets"))
            add(WheelColumn("", targets.map { com.fitnex.feature.workout.ui.formatTargetValue(tracking, it) }, targets.indexOf(current).coerceAtLeast(0), 1f, "wheel_reps"))
            add(WheelColumn("", rests.map(::formatRest), rests.indexOf(item.restSeconds).coerceAtLeast(0), 1f, "wheel_rest"))
            if (weighted) add(
                WheelColumn(
                    "", WEIGHT_OPTIONS.map { it?.let(::formatKg) ?: "—" },
                    item.targetWeightKg?.let { w -> WEIGHT_OPTIONS.indexOfFirst { it != null && it >= w } }?.coerceAtLeast(0) ?: 0,
                    1f, "wheel_weight",
                ),
            )
        }
    }
    val targetCaption = stringResource(
        when (tracking) {
            com.fitnex.core.model.Tracking.TIME -> R.string.editor_time
            com.fitnex.core.model.Tracking.TIME_DISTANCE -> R.string.editor_minutes
            else -> R.string.editor_reps
        },
    )
    val captions = listOf(stringResource(R.string.editor_sets), targetCaption, stringResource(R.string.editor_rest), stringResource(R.string.editor_weight))
    FitnexMultiWheelSheet(
        title = item.exerciseName,
        columns = columns.mapIndexed { i, c -> c.copy(caption = captions[i]) },
        onConfirm = { idx -> onConfirm(sets[idx[0]], targets[idx[1]], rests[idx[2]], if (weighted) WEIGHT_OPTIONS[idx[3]] else null) },
        onDismiss = onDismiss,
    )
}
