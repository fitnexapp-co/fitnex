package com.fitnex.feature.workout.live

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexConfirmDialog
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexProgressRing
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.picker.FitnexMultiWheelSheet
import com.fitnex.core.designsystem.component.picker.WheelColumn
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.LoggedSet
import com.fitnex.feature.workout.R
import com.fitnex.feature.workout.ui.formatKg
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val REPS_OPTIONS = (0..60).toList()
private val KG_OPTIONS = (0..600).map { it * 0.5f }

@Composable
internal fun LiveWorkoutScreen(
    state: LiveUiState,
    vm: LiveWorkoutViewModel,
    notificationsEnabled: Boolean,
    onClose: () -> Unit,
    onFinished: (String) -> Unit,
) {
    val colors = FitnexTheme.colors
    LaunchedEffect(state.finishedSummaryId, state.closed) {
        state.finishedSummaryId?.let(onFinished)
        if (state.closed) onClose()
    }
    // Gym use: keep the screen awake during the workout.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Ask for the notification permission in context, only if the user wants rest notifications.
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(notificationsEnabled) {
        if (notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AndroidRestAlerts.canNotify(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var editingDraft by rememberSaveable { mutableStateOf(false) }
    var countWithCamera by rememberSaveable { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<LoggedSet?>(null) }
    var pickExercise by rememberSaveable { mutableStateOf(false) }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val guides by vm.guides.collectAsStateWithLifecycle()
    val newRecord by vm.newRecord.collectAsStateWithLifecycle()

    val session = state.session
    if (state.loading || session == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) { LoadingState() }
        return
    }
    val item = state.current

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // ---- Top bar: close, routine + elapsed time, pause
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("live_close")) {
                Icon(Icons.Rounded.Close, stringResource(R.string.live_close), tint = colors.textPrimary)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(session.routineName, style = FitnexTheme.typography.labelMedium, color = colors.accent)
                Text(
                    formatClock(session.elapsed(state.now).seconds),
                    style = FitnexTheme.typography.headlineLarge,
                    color = if (session.isPaused) colors.textDisabled else colors.textPrimary,
                    modifier = Modifier.testTag("live_elapsed"),
                )
                if (session.isPaused) Text(stringResource(R.string.live_paused), style = FitnexTheme.typography.labelSmall, color = colors.warning)
            }
            if (session.isPaused) {
                IconButton(onClick = vm::resumeSession, modifier = Modifier.testTag("live_resume")) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.live_resume), tint = colors.accent)
                }
            } else {
                val pauseLabel = stringResource(R.string.live_pause)
                IconButton(onClick = vm::pauseSession, modifier = Modifier.testTag("live_pause")) {
                    Icon(com.fitnex.core.designsystem.icon.FitnexIcons.Pause, pauseLabel, tint = colors.textPrimary)
                }
            }
        }
        // ---- Overall progress
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            Text(
                stringResource(R.string.live_progress, state.index + 1, session.plan.size, state.doneSets, state.plannedSets),
                style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
                modifier = Modifier.testTag("live_progress"),
            )
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { if (state.plannedSets == 0) 0f else (state.doneSets.toFloat() / state.plannedSets).coerceIn(0f, 1f) },
                color = colors.accent, trackColor = colors.textDisabled.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (item == null) return@Column
            // ---- Current exercise
            GlassCard(Modifier.fillMaxWidth()) {
                // How the exercise looks: start/end photos when we have them, else its type icon.
                val exercise = guides[item.exerciseId]
                val media = exercise?.mediaUri
                if (media != null) {
                    com.fitnex.feature.workout.media.ExerciseMotion(
                        media, item.exerciseName,
                        Modifier.padding(bottom = Spacing.sm).testTag("live_media"), aspect = 1.9f,
                    )
                } else if (exercise != null) {
                    com.fitnex.feature.workout.media.ExerciseThumbnail(
                        null, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.xs).testTag("live_media_icon"),
                        size = 56.dp, placeholder = com.fitnex.feature.workout.exerciseIcon(exercise),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previousExercise, enabled = state.index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowLeft, stringResource(R.string.live_prev_exercise), tint = if (state.index > 0) colors.textPrimary else colors.textDisabled)
                    }
                    Column(
                        Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.live_change_exercise)) { pickExercise = true },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(item.exerciseName, style = FitnexTheme.typography.headlineMedium, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.testTag("live_exercise_name"))
                        if (state.primaryMuscles.isNotEmpty()) {
                            Text(state.primaryMuscles.joinToString(" · "), style = FitnexTheme.typography.bodySmall, color = colors.accent)
                        }
                    }
                    IconButton(onClick = vm::next, enabled = state.index < session.plan.lastIndex, modifier = Modifier.testTag("live_next")) {
                        Icon(Icons.Rounded.KeyboardArrowRight, stringResource(R.string.live_next_exercise), tint = if (state.index < session.plan.lastIndex) colors.textPrimary else colors.textDisabled)
                    }
                }
                Text(
                    previousText(state),
                    style = FitnexTheme.typography.bodySmall, color = colors.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("live_previous"),
                )
                // Your bests for this exercise, from earlier workouts.
                state.bests?.let { BestsRow(it) }
                // Technique without leaving the session.
                if (guides[item.exerciseId]?.instructions?.isNotEmpty() == true) {
                    TextButton(onClick = { showGuide = true }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("live_guide")) {
                        Icon(Icons.Rounded.Info, null, tint = colors.accent, modifier = Modifier.padding(end = Spacing.xs))
                        Text(stringResource(R.string.live_how_to), color = colors.accent, style = FitnexTheme.typography.labelLarge)
                    }
                }
            }

            // ---- A personal record, the moment it happens
            newRecord?.let { RecordBanner(it, onDismiss = vm::dismissRecord) }

            // ---- Rest timer (shown right where the eyes are after a set)
            state.rest?.let { RestCard(it, state, vm) }

            // ---- Sets
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                state.currentSets.forEach { set ->
                    SetRow(
                        label = stringResource(R.string.live_set_label, set.setNumber),
                        value = setText(state, set.reps, set.weightKg, set.durationSeconds, set.distanceMeters),
                        done = true,
                        modifier = Modifier.testTag("live_set_${set.setNumber}").clickable { editingSet = set },
                    )
                }
                val nextNumber = state.currentSets.size + 1
                val rulers = state.tracking == com.fitnex.core.model.Tracking.WEIGHT_REPS || state.tracking == com.fitnex.core.model.Tracking.REPS
                if (nextNumber <= state.targetSets && rulers) {
                    // Weight and reps picked right here, on two rulers you slide with the finger.
                    DraftRulers(state, nextNumber) { reps, kg -> vm.setDraft(reps, kg) }
                    for (n in nextNumber + 1..state.targetSets) {
                        SetRow(stringResource(R.string.live_set_label, n), setText(state, item.reps, pendingWeight(state, item.targetWeightKg), targetSeconds(state.tracking, item.reps), null), pending = true)
                    }
                } else if (nextNumber <= state.targetSets) {
                    SetRow(
                        label = stringResource(R.string.live_set_label, nextNumber),
                        value = setText(state, state.draft.reps, state.draft.weightKg, state.draft.durationSeconds, state.draft.distanceMeters),
                        sub = when {
                            state.draft.source == com.fitnex.core.model.SetSource.CAMERA -> stringResource(R.string.camera_counted)
                            state.draft.increaseKg != null -> stringResource(R.string.live_overload, formatKg(state.draft.increaseKg))
                            else -> stringResource(R.string.live_next_set)
                        },
                        highlighted = true,
                        modifier = Modifier.testTag("live_draft").clickable { editingDraft = true },
                    )
                    for (n in nextNumber + 1..state.targetSets) {
                        SetRow(stringResource(R.string.live_set_label, n), setText(state, item.reps, pendingWeight(state, item.targetWeightKg), targetSeconds(state.tracking, item.reps), null), pending = true)
                    }
                } else {
                    Text(stringResource(R.string.live_all_done), style = FitnexTheme.typography.bodyMedium, color = colors.success, modifier = Modifier.padding(Spacing.sm))
                }
            }
            if (state.cameraSupported && state.currentSets.size < state.targetSets) {
                FitnexSecondaryButton(
                    stringResource(R.string.camera_count_action), { countWithCamera = true },
                    Modifier.fillMaxWidth().testTag("live_camera_count"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // Two clear actions; moving on is the → arrow next to the exercise name.
                FitnexSecondaryButton(stringResource(R.string.live_repeat), vm::repeatLastSet, Modifier.weight(1f).testTag("live_repeat"), enabled = state.currentSets.isNotEmpty())
                FitnexSecondaryButton(stringResource(R.string.live_add_set), vm::addSet, Modifier.weight(1f).testTag("live_add_set"))
            }
            TextButton(onClick = { confirmFinish = true }, modifier = Modifier.fillMaxWidth().testTag("live_finish")) {
                Text(stringResource(R.string.live_finish), color = colors.error, style = FitnexTheme.typography.labelLarge)
            }
        }

        // ---- The one big action
        Box(Modifier.fillMaxWidth().padding(Spacing.gutter)) {
            // Once every planned set is logged, the big button moves you on instead of adding sets
            // ("+ Serie" is still there for an extra one).
            val exerciseDone = item != null && state.currentSets.size >= state.targetSets
            when {
                exerciseDone && state.index < session.plan.lastIndex -> FitnexPrimaryButton(
                    stringResource(R.string.live_next_exercise), vm::next,
                    Modifier.fillMaxWidth().testTag("live_next_big"), enabled = !session.isPaused,
                )
                exerciseDone -> FitnexPrimaryButton(
                    stringResource(R.string.live_finish), { confirmFinish = true },
                    Modifier.fillMaxWidth().testTag("live_finish_big"),
                )
                else -> FitnexPrimaryButton(
                    stringResource(R.string.live_complete_set), { vm.completeSet() },
                    Modifier.fillMaxWidth().testTag("live_complete"),
                    enabled = item != null && !session.isPaused,
                )
            }
        }
    }

    // ---- Sheets & dialogs
    if (countWithCamera && item != null) {
        com.fitnex.feature.workout.camera.CameraRepCounterDialog(
            exerciseId = item.exerciseId,
            exerciseName = item.exerciseName,
            onUse = { n -> vm.useCameraReps(n); countWithCamera = false },
            onDismiss = { countWithCamera = false },
        )
    }
    if (editingDraft && item != null) {
        SetSheet(
            stringResource(R.string.live_draft_title), state, state.draft.reps, state.draft.weightKg, state.draft.durationSeconds, state.draft.distanceMeters,
            onDismiss = { editingDraft = false },
            onConfirm = { d -> vm.setDraft(d.reps, d.weightKg, d.durationSeconds, d.distanceMeters); editingDraft = false },
        )
    }
    editingSet?.let { set ->
        SetSheet(
            stringResource(R.string.live_edit_set, set.setNumber), state, set.reps, set.weightKg, set.durationSeconds, set.distanceMeters,
            onDismiss = { editingSet = null },
            onConfirm = { d -> vm.editSet(set.id, d.reps, d.weightKg, d.durationSeconds, d.distanceMeters); editingSet = null },
            onDelete = { vm.deleteSet(set.id); editingSet = null },
        )
    }
    if (pickExercise) {
        FitnexBottomSheet(onDismiss = { pickExercise = false }, title = stringResource(R.string.live_change_exercise)) {
            session.plan.forEachIndexed { i, e ->
                val done = session.setsFor(e.exerciseId).size
                Row(
                    Modifier.fillMaxWidth().clickable { vm.goTo(i); pickExercise = false }.padding(vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}. ${e.exerciseName}", style = FitnexTheme.typography.titleMedium,
                        color = if (i == state.index) colors.accent else colors.textPrimary, modifier = Modifier.weight(1f))
                    Text("$done/${e.sets}", style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
    }
    if (showGuide && item != null) {
        guides[item.exerciseId]?.let { e -> ExerciseGuideSheet(e, onDismiss = { showGuide = false }) }
    }
    if (confirmFinish) {
        FitnexConfirmDialog(
            title = stringResource(R.string.live_finish_title),
            message = if (state.doneSets == 0) stringResource(R.string.live_finish_empty)
            else stringResource(R.string.live_finish_body, state.doneSets, state.plannedSets),
            confirmLabel = stringResource(R.string.live_finish_confirm),
            dismissLabel = stringResource(R.string.live_keep_going),
            onConfirm = { confirmFinish = false; vm.finish() },
            onDismiss = { confirmFinish = false },
        )
    }
}

@Composable
private fun previousText(state: LiveUiState): String {
    val prev = state.previous ?: return stringResource(R.string.live_first_time)
    val locale = rememberAppLocale()
    val date = prev.date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM", locale))
    val sets = prev.sets.joinToString(" · ") {
        when {
            it.durationSeconds != null -> listOfNotNull(com.fitnex.feature.workout.ui.formatSeconds(it.durationSeconds!!), it.distanceMeters?.takeIf { m -> m > 0f }?.let { d -> com.fitnex.feature.workout.ui.formatDistance(d) }).joinToString(" ")
            it.weightKg > 0f -> "${it.reps}×${formatKg(it.weightKg)}"
            else -> "${it.reps}"
        }
    }
    return stringResource(R.string.live_last_time, date, sets)
}

/**
 * Weight shown on the sets still to do: the weight proposed for the next set (last time's weight,
 * with any suggested increase), so they don't read "sin peso" when the routine has no target;
 * otherwise the routine's target.
 */
internal fun pendingWeight(state: LiveUiState, targetWeightKg: Float?): Float =
    state.draft.weightKg.takeIf { it > 0f } ?: targetWeightKg ?: 0f

@Composable
private fun setValue(reps: Int, kg: Float): String =
    if (kg > 0f) stringResource(R.string.live_set_value, reps, formatKg(kg)) else stringResource(R.string.live_set_value_bw, reps)

@Composable
private fun SetRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    done: Boolean = false,
    highlighted: Boolean = false,
    pending: Boolean = false,
) {
    val colors = FitnexTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (highlighted) Modifier.border(1.dp, colors.accent, RoundedCornerShape(Radii.sm)).background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(Radii.sm))
                else Modifier,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FitnexTheme.typography.labelMedium, color = if (pending) colors.textDisabled else colors.textSecondary)
            Text(value, style = FitnexTheme.typography.titleLarge, color = if (pending) colors.textDisabled else colors.textPrimary)
            if (sub != null) Text(sub, style = FitnexTheme.typography.bodySmall, color = colors.accent)
        }
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = colors.success)
    }
}

@Composable
private fun RestCard(rest: RestTimer, state: LiveUiState, vm: LiveWorkoutViewModel) {
    val colors = FitnexTheme.colors
    val remaining = rest.remaining(state.now)
    val label = formatClock(remaining)
    val a11y = stringResource(R.string.live_rest_remaining, label)
    // The ring empties at a constant speed (one linear animation down to the end time) while the
    // number changes once a second. Only the ring redraws; nothing recomposes.
    val fraction = remember { androidx.compose.animation.core.Animatable(1f) }
    val latestNow by androidx.compose.runtime.rememberUpdatedState(state.now)
    androidx.compose.runtime.LaunchedEffect(rest) {
        val totalMs = rest.totalSeconds * 1000f
        val endsAt = rest.endsAt
        when {
            totalMs <= 0f -> fraction.snapTo(0f)
            endsAt == null -> fraction.snapTo(((rest.pausedRemaining ?: 0L) * 1000f / totalMs).coerceIn(0f, 1f))
            else -> {
                val leftMs = java.time.Duration.between(latestNow, endsAt).toMillis().coerceIn(0L, totalMs.toLong())
                fraction.snapTo(leftMs / totalMs)
                fraction.animateTo(0f, androidx.compose.animation.core.tween(leftMs.toInt(), easing = androidx.compose.animation.core.LinearEasing))
            }
        }
    }
    // Layout in rows (not one long row) so every control stays reachable on narrow phones.
    GlassCard(Modifier.fillMaxWidth().testTag("live_rest")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitnexProgressRing(
                progress = { fraction.value },
                size = 88.dp,
                accessibilityLabel = a11y,
            ) {
                Text(label, style = FitnexTheme.typography.titleLarge, color = colors.textPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("live_rest_time"))
            }
            Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                Text(stringResource(R.string.live_rest), style = FitnexTheme.typography.titleMedium, color = colors.accent)
                Text(stringResource(R.string.live_rest_hint), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { vm.addRest(-15) }) { Text(stringResource(R.string.live_rest_minus), color = colors.textPrimary) }
            TextButton(onClick = { vm.addRest(15) }, modifier = Modifier.testTag("live_rest_plus")) { Text(stringResource(R.string.live_rest_plus), color = colors.textPrimary) }
            TextButton(onClick = { if (rest.isPaused) vm.resumeRest() else vm.pauseRest() }, modifier = Modifier.testTag("live_rest_toggle")) {
                Text(stringResource(if (rest.isPaused) R.string.live_rest_resume else R.string.live_rest_pause), color = colors.textPrimary)
            }
        }
        FitnexSecondaryButton(
            stringResource(R.string.live_rest_skip), vm::skipRest,
            Modifier.fillMaxWidth().padding(top = Spacing.xs).testTag("live_rest_skip"),
        )
    }
}

@Composable
private fun RepsKgSheet(
    title: String,
    reps: Int,
    kg: Float,
    onDismiss: () -> Unit,
    onConfirm: (Int, Float) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val columns = remember(reps, kg) {
        listOf(
            WheelColumn("", REPS_OPTIONS.map(Int::toString), REPS_OPTIONS.indexOf(reps).coerceAtLeast(0), 1f, "wheel_live_reps"),
            WheelColumn("", KG_OPTIONS.map { if (it == 0f) "—" else formatKg(it) }, KG_OPTIONS.indexOfFirst { it >= kg }.coerceAtLeast(0), 1f, "wheel_live_kg"),
        )
    }
    val captions = listOf(stringResource(R.string.live_reps), stringResource(R.string.live_kg))
    FitnexMultiWheelSheet(
        title = title,
        columns = columns.mapIndexed { i, c -> c.copy(caption = captions[i]) },
        onConfirm = { idx -> onConfirm(REPS_OPTIONS[idx[0]], KG_OPTIONS[idx[1]]) },
        onDismiss = onDismiss,
        footer = {
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().testTag("live_delete_set")) {
                    Text(stringResource(R.string.live_delete_set), color = FitnexTheme.colors.error)
                }
            }
        },
    )
}

/** The next set, edited in place: weight and reps on horizontal rulers (reps only for bodyweight). */
@Composable
private fun DraftRulers(state: LiveUiState, number: Int, onChange: (Int, Float) -> Unit) {
    val colors = FitnexTheme.colors
    val draft = state.draft
    val withWeight = state.tracking == com.fitnex.core.model.Tracking.WEIGHT_REPS
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(1.dp, colors.accent, RoundedCornerShape(Radii.sm))
            .background(colors.accent.copy(alpha = 0.06f), RoundedCornerShape(Radii.sm))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag("live_draft"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.live_set_label, number), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(setText(state, draft.reps, draft.weightKg, null, null), style = FitnexTheme.typography.labelMedium, color = colors.textPrimary)
        }
        when {
            draft.source == com.fitnex.core.model.SetSource.CAMERA -> Text(stringResource(R.string.camera_counted), style = FitnexTheme.typography.bodySmall, color = colors.accent)
            draft.increaseKg != null -> Text(stringResource(R.string.live_overload, formatKg(draft.increaseKg)), style = FitnexTheme.typography.bodySmall, color = colors.accent)
        }
        if (withWeight) {
            com.fitnex.core.designsystem.component.picker.FitnexRulerPicker(
                label = stringResource(R.string.live_ruler_weight),
                value = draft.weightKg,
                onValueChange = { kg -> onChange(draft.reps, kg) },
                min = 0f, max = 300f, step = 0.5f, majorEvery = 10,
                unit = "kg", format = { formatKg(it) },
                testTag = "ruler_kg",
            )
        }
        com.fitnex.core.designsystem.component.picker.FitnexRulerPicker(
            label = stringResource(R.string.live_ruler_reps),
            value = draft.reps.toFloat(),
            onValueChange = { r -> onChange(r.toInt(), draft.weightKg) },
            min = 0f, max = 100f, step = 1f, majorEvery = 5,
            unit = stringResource(R.string.live_reps).lowercase(),
            testTag = "ruler_reps",
        )
    }
}

/** "Mejor 40 kg · 1RM est. 46 kg · Máx. 12 reps": quiet chips, only what exists. */
@Composable
private fun BestsRow(b: ExerciseBests) {
    val colors = FitnexTheme.colors
    val items = listOfNotNull(
        b.bestKg?.let { stringResource(R.string.live_best_kg, formatKg(it)) },
        b.estimated1rm?.let { stringResource(R.string.live_best_1rm, formatKg(Math.round(it * 2) / 2f)) },
        b.bestReps?.let { stringResource(R.string.live_best_reps, it) },
    )
    if (items.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("live_bests"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        items.forEach { t ->
            Text(
                t, style = FitnexTheme.typography.labelSmall, color = colors.textPrimary,
                modifier = Modifier.background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = Spacing.sm, vertical = 3.dp),
            )
        }
    }
}

/** Seconds the routine target stands for: the reps field holds seconds (timed) or minutes (cardio). */
private fun targetSeconds(tracking: com.fitnex.core.model.Tracking, reps: Int): Int? = when (tracking) {
    com.fitnex.core.model.Tracking.TIME -> reps
    com.fitnex.core.model.Tracking.TIME_DISTANCE -> reps * 60
    else -> null
}

/** One set as text, in the exercise's own terms: "10 reps × 40 kg", "12 reps", "45 s", "20 min · 3,2 km". */
@Composable
private fun setText(state: LiveUiState, reps: Int, kg: Float, seconds: Int?, meters: Float?): String = when (state.tracking) {
    com.fitnex.core.model.Tracking.TIME -> {
        val t = com.fitnex.feature.workout.ui.formatSeconds(seconds ?: 0)
        if (kg > 0f) stringResource(R.string.live_set_time_kg, t, formatKg(kg)) else t
    }
    com.fitnex.core.model.Tracking.TIME_DISTANCE ->
        listOfNotNull(com.fitnex.feature.workout.ui.formatSeconds(seconds ?: 0), meters?.takeIf { it > 0f }?.let { d -> com.fitnex.feature.workout.ui.formatDistance(d) }).joinToString(" · ")
    com.fitnex.core.model.Tracking.REPS ->
        if (kg > 0f) stringResource(R.string.live_set_value, reps, formatKg(kg)) else stringResource(R.string.live_set_reps_only, reps)
    else -> setValue(reps, kg)
}

private val MINUTE_OPTIONS = (0..59).toList()
private val CARDIO_MINUTE_OPTIONS = (0..300).toList()
private val SECOND_OPTIONS = (0..55 step 5).toList()
private val KM_OPTIONS = (0..500).map { it / 10f }

/** The set editor for the exercise's tracking mode (weight × reps, reps, time, time + distance). */
@Composable
private fun SetSheet(
    title: String,
    state: LiveUiState,
    reps: Int,
    kg: Float,
    seconds: Int?,
    meters: Float?,
    onDismiss: () -> Unit,
    onConfirm: (SetDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val tracking = state.tracking
    if (tracking == com.fitnex.core.model.Tracking.WEIGHT_REPS) {
        RepsKgSheet(title, reps, kg, onDismiss, { r, w -> onConfirm(SetDraft(r, w)) }, onDelete)
        return
    }
    val total = seconds ?: 0
    val columns = remember(tracking, reps, kg, seconds, meters, state.timedWeight) {
        when (tracking) {
            com.fitnex.core.model.Tracking.REPS -> listOf(
                WheelColumn("", REPS_OPTIONS.map(Int::toString), REPS_OPTIONS.indexOf(reps).coerceAtLeast(0), 1f, "wheel_live_reps"),
            )
            com.fitnex.core.model.Tracking.TIME -> buildList {
                add(WheelColumn("", MINUTE_OPTIONS.map(Int::toString), (total / 60).coerceIn(0, 59), 1f, "wheel_live_min"))
                add(WheelColumn("", SECOND_OPTIONS.map { "%02d".format(it) }, SECOND_OPTIONS.indexOfFirst { it >= total % 60 }.coerceAtLeast(0), 1f, "wheel_live_sec"))
                if (state.timedWeight) add(WheelColumn("", KG_OPTIONS.map { if (it == 0f) "—" else formatKg(it) }, KG_OPTIONS.indexOfFirst { it >= kg }.coerceAtLeast(0), 1f, "wheel_live_kg"))
            }
            else -> listOf(
                WheelColumn("", CARDIO_MINUTE_OPTIONS.map(Int::toString), (total / 60).coerceIn(0, 300), 1f, "wheel_live_min"),
                WheelColumn("", KM_OPTIONS.map { if (it == 0f) "—" else "%.1f".format(java.util.Locale("es"), it) }, KM_OPTIONS.indexOfFirst { it * 1000f >= (meters ?: 0f) - 1f }.coerceAtLeast(0), 1f, "wheel_live_km"),
            )
        }
    }
    val captions = when (tracking) {
        com.fitnex.core.model.Tracking.REPS -> listOf(stringResource(R.string.live_reps))
        com.fitnex.core.model.Tracking.TIME -> listOf(stringResource(R.string.live_minutes), stringResource(R.string.live_seconds), stringResource(R.string.live_kg))
        else -> listOf(stringResource(R.string.live_minutes), stringResource(R.string.live_distance))
    }
    FitnexMultiWheelSheet(
        title = title,
        columns = columns.mapIndexed { i, c -> c.copy(caption = captions[i]) },
        onConfirm = { idx ->
            onConfirm(
                when (tracking) {
                    com.fitnex.core.model.Tracking.REPS -> SetDraft(REPS_OPTIONS[idx[0]], 0f)
                    com.fitnex.core.model.Tracking.TIME -> SetDraft(
                        0, if (state.timedWeight) KG_OPTIONS[idx[2]] else 0f,
                        durationSeconds = MINUTE_OPTIONS[idx[0]] * 60 + SECOND_OPTIONS[idx[1]],
                    )
                    else -> SetDraft(0, 0f, durationSeconds = CARDIO_MINUTE_OPTIONS[idx[0]] * 60, distanceMeters = KM_OPTIONS[idx[1]] * 1000f)
                },
            )
        },
        onDismiss = onDismiss,
        footer = {
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().testTag("live_delete_set")) {
                    Text(stringResource(R.string.live_delete_set), color = FitnexTheme.colors.error)
                }
            }
        },
    )
}

/** "¡Nuevo récord!" with what it beat. Quiet colours: it informs, it does not interrupt. */
@Composable
private fun RecordBanner(r: com.fitnex.core.model.PersonalRecord, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    val detail = when (r.type) {
        com.fitnex.core.model.RecordType.MAX_WEIGHT -> stringResource(R.string.record_weight, formatKg(r.value), formatKg(r.previous ?: 0f))
        com.fitnex.core.model.RecordType.ESTIMATED_1RM -> stringResource(R.string.record_1rm, formatKg(r.value), formatKg(r.previous ?: 0f))
        com.fitnex.core.model.RecordType.MAX_REPS -> stringResource(R.string.record_reps, r.value.toInt().toString(), (r.previous ?: 0f).toInt().toString())
    }
    GlassCard(Modifier.fillMaxWidth().testTag("live_record").semantics { liveRegion = LiveRegionMode.Polite }, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(com.fitnex.core.designsystem.icon.FitnexIcons.Trophy, null, tint = colors.success, modifier = Modifier.padding(end = Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.live_record_title), style = FitnexTheme.typography.titleMedium, color = colors.success)
                Text(detail, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.live_record_dismiss), tint = colors.textSecondary) }
        }
    }
}

/** Steps, tips and common mistakes of the current exercise, over the session (nothing is lost). */
@Composable
private fun ExerciseGuideSheet(e: com.fitnex.core.model.Exercise, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    FitnexBottomSheet(onDismiss = onDismiss, title = e.name) {
        Column(Modifier.verticalScroll(rememberScrollState()).testTag("live_guide_sheet"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.live_guide_steps), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
            e.instructions.forEachIndexed { i, s -> Text("${i + 1}. $s", style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary) }
            if (e.techniqueTips.isNotEmpty()) {
                Text(stringResource(R.string.live_guide_tips), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
                e.techniqueTips.forEach { Text("• $it", style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary) }
            }
            if (e.commonMistakes.isNotEmpty()) {
                Text(stringResource(R.string.live_guide_mistakes), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
                e.commonMistakes.forEach { Text("• $it", style = FitnexTheme.typography.bodyMedium, color = colors.warning) }
            }
            FitnexPrimaryButton(stringResource(R.string.live_guide_back), onDismiss, Modifier.fillMaxWidth().padding(vertical = Spacing.md))
        }
    }
}