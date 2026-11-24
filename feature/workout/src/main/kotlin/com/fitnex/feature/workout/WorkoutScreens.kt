package com.fitnex.feature.workout

import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import com.fitnex.core.designsystem.component.liquidRim
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import com.fitnex.core.anatomy.MuscleMapCard
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.component.CalendarHeaderAction
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexChipGroup
import com.fitnex.core.designsystem.component.FitnexHeader
import com.fitnex.core.designsystem.component.FitnexPrimaryButton
import com.fitnex.core.designsystem.component.FitnexSecondaryButton
import com.fitnex.core.designsystem.component.FitnexTextField
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LoadingState
import com.fitnex.core.designsystem.component.MessageState
import com.fitnex.core.designsystem.component.ProfileHeaderAction
import com.fitnex.core.designsystem.component.SectionHeader
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Radii
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.AnatomicalRegion
import com.fitnex.core.model.Difficulty
import com.fitnex.core.model.Equipment
import com.fitnex.core.model.Exercise
import com.fitnex.core.model.ExerciseListItem
import com.fitnex.core.model.ExerciseType
import com.fitnex.core.model.MuscleInvolvement
import com.fitnex.core.model.Routine
import com.fitnex.feature.workout.ui.daysLabel
import com.fitnex.feature.workout.ui.label

// ---------------------------------------------------------------- Home

@Composable
internal fun WorkoutHomeScreen(
    state: WorkoutHomeUiState,
    contentPadding: PaddingValues,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onNewRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onStartRoutine: (String) -> Unit,
    onResumeWorkout: () -> Unit,
) {
    val colors = FitnexTheme.colors
    var showAiRoutine by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding),
    ) {
        com.fitnex.core.designsystem.component.FitnexSectionHero(
            title = stringResource(R.string.workout_title),
            subtitle = stringResource(R.string.workout_subtitle),
            overline = stringResource(R.string.workout_overline),
            overlineIcon = FitnexIcons.Workout,
            actions = {
                CalendarHeaderAction(onOpenCalendar, Modifier.testTag("open_calendar"))
                ProfileHeaderAction(onOpenSettings, Modifier.testTag("open_settings"))
            },
        )
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val featured = state.featured
            if (featured != null) {
                PlanCard(
                    routine = featured, info = state.routineInfo[featured.id], isToday = state.featuredIsToday,
                    doneToday = state.today?.doneToday == true, resuming = state.active != null,
                    onStart = { if (state.active != null) onResumeWorkout() else onStartRoutine(featured.id) },
                    onOpen = { onOpenRoutine(featured.id) },
                )
            }
            state.active?.let { active ->
                // Recovery after a crash / app close (spec §18): the session is waiting in Room.
                GlassCard(onClick = onResumeWorkout, modifier = Modifier.fillMaxWidth().testTag("resume_workout")) {
                    Text(stringResource(R.string.live_resume_title), style = FitnexTheme.typography.labelMedium, color = colors.accent)
                    Text(
                        stringResource(
                            R.string.live_resume_body, active.routineName,
                            com.fitnex.feature.workout.live.formatClock(active.elapsed(java.time.Instant.now()).seconds),
                            active.sets.size,
                        ),
                        style = FitnexTheme.typography.titleMedium, color = colors.textPrimary,
                    )
                    FitnexPrimaryButton(stringResource(R.string.live_resume_action), onResumeWorkout, Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
            }
            // Shortcuts: icon, title, chevron, hairline between rows.
            Column {
                com.fitnex.core.designsystem.component.SettingsRow(
                    title = stringResource(R.string.workout_library_title),
                    subtitle = if (state.libraryCount > 0) stringResource(R.string.workout_library_body, state.libraryCount) else null,
                    icon = FitnexIcons.Workout,
                    onClick = onOpenLibrary,
                    modifier = Modifier.testTag("open_library"),
                )
                com.fitnex.core.designsystem.component.SettingsDivider()
                com.fitnex.core.designsystem.component.SettingsRow(
                    title = stringResource(R.string.ai_routine_card_title),
                    subtitle = stringResource(R.string.ai_routine_card_body),
                    icon = FitnexIcons.Sparkle,
                    onClick = { showAiRoutine = true },
                    modifier = Modifier.testTag("ai_routine_card"),
                )
                if (state.routines.isNotEmpty()) {
                    com.fitnex.core.designsystem.component.SettingsDivider()
                    com.fitnex.core.designsystem.component.SettingsRow(
                        title = stringResource(R.string.workout_new_routine),
                        icon = Icons.Rounded.Add,
                        onClick = onNewRoutine,
                        modifier = Modifier.testTag("new_routine"),
                    )
                }
            }
            if (showAiRoutine) {
                com.fitnex.feature.workout.ai.AiRoutineSheet(
                    onDismiss = { showAiRoutine = false },
                    onSaved = { id -> showAiRoutine = false; onOpenRoutine(id) },
                )
            }

            // History and planning live in the calendar (header button): no second button here.
            Text(
                stringResource(R.string.workout_your_workouts),
                style = FitnexTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = colors.textPrimary, modifier = Modifier.padding(top = Spacing.sm),
            )
            when {
                state.loading -> LoadingState()
                state.routines.isEmpty() -> GlassCard {
                    MessageState(
                        icon = FitnexIcons.Workout,
                        title = stringResource(R.string.workout_empty_title),
                        message = stringResource(R.string.workout_empty_message),
                        actionLabel = stringResource(R.string.workout_create_routine),
                        onAction = onNewRoutine,
                    )
                }
                else -> state.routines.forEach { r ->
                    RoutineRow(
                        r, state.routineInfo[r.id],
                        onClick = { onOpenRoutine(r.id) },
                        onStart = { if (state.active != null) onResumeWorkout() else onStartRoutine(r.id) },
                    )
                }
            }
            // Only once there is something to train: an empty account starts with "Crear rutina".
            if (state.weekTarget > 0 && state.routines.isNotEmpty()) ConsistencyCard(state.weekDone, state.weekTarget)
        }
    }
}

/** Hero: the routine to do now, what it trains, how long it takes and one big button. */
@Composable
private fun PlanCard(
    routine: Routine,
    info: RoutineInfo?,
    isToday: Boolean,
    doneToday: Boolean,
    resuming: Boolean,
    onStart: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = FitnexTheme.colors
    GlassCard(
        onClick = onOpen, modifier = Modifier.fillMaxWidth().testTag("today_card"),
        emphasis = com.fitnex.core.designsystem.component.CardEmphasis.Featured, contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.Transparent, colors.accent.copy(alpha = 0.08f))),
            ),
        ) {
            // Decorative equipment, faint, behind the text.
            Icon(
                FitnexIcons.Kettlebell, null, tint = colors.accent.copy(alpha = 0.16f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Spacing.md, end = Spacing.md).size(120.dp),
            )
            Column(Modifier.padding(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = colors.accent)
                    }
                    Badge(stringResource(if (doneToday) R.string.plan_done_today else if (isToday) R.string.today_title else R.string.plan_current))
                }
                Text(
                    routine.name, style = FitnexTheme.typography.headlineLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = colors.textPrimary, modifier = Modifier.padding(top = Spacing.md), maxLines = 2,
                )
                val groups = info?.groups.orEmpty()
                if (groups.isNotEmpty()) {
                    Text(joinNatural(groups.map { it.label() }), style = FitnexTheme.typography.bodyLarge, color = colors.textSecondary)
                }
                Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(FitnexIcons.Chart, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Text(
                        pluralStringResource(R.plurals.workout_exercise_count, routine.exercises.size, routine.exercises.size),
                        style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(start = Spacing.xs),
                    )
                    if (info != null && info.estimatedMinutes > 0) {
                        Box(Modifier.padding(horizontal = Spacing.md).size(width = 1.dp, height = 22.dp).background(colors.divider))
                        Icon(FitnexIcons.Clock, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                        Text(
                            stringResource(R.string.plan_minutes, info.estimatedMinutes),
                            style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(start = Spacing.xs).testTag("plan_minutes"),
                        )
                    }
                }
                FitnexPrimaryButton(
                    stringResource(if (resuming) R.string.plan_resume else R.string.plan_start), onStart,
                    Modifier.fillMaxWidth().padding(top = Spacing.lg).testTag("today_start"), icon = Icons.Rounded.PlayArrow,
                )
            }
        }
    }
}

/** "Pecho, hombros y tríceps". */
@Composable
private fun joinNatural(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " " + stringResource(R.string.list_and) + " " + parts.last().replaceFirstChar { it.lowercase() }
}.let { s -> s.lowercase().replaceFirstChar { it.titlecase() } }

/** One routine: icon by what it trains, last time done, a start button and a chevron to edit. */
@Composable
private fun RoutineRow(routine: Routine, info: RoutineInfo?, onClick: () -> Unit, onStart: () -> Unit) {
    val colors = FitnexTheme.colors
    val locale = com.fitnex.core.designsystem.locale.rememberAppLocale()
    val last = info?.lastDone
    val today = java.time.LocalDate.now()
    val lastDay = last?.startedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
    val doneToday = lastDay == today
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("routine_${routine.name}"), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val legs = setOf(
                com.fitnex.feature.workout.library.MuscleGroup.QUADS, com.fitnex.feature.workout.library.MuscleGroup.HAMSTRINGS,
                com.fitnex.feature.workout.library.MuscleGroup.GLUTES, com.fitnex.feature.workout.library.MuscleGroup.CALVES,
                com.fitnex.feature.workout.library.MuscleGroup.ADDUCTORS, com.fitnex.feature.workout.library.MuscleGroup.ABDUCTORS,
            )
            val pull = setOf(com.fitnex.feature.workout.library.MuscleGroup.BACK, com.fitnex.feature.workout.library.MuscleGroup.BICEPS, com.fitnex.feature.workout.library.MuscleGroup.FOREARMS)
            val icon = when (info?.groups?.firstOrNull()) {
                in legs -> FitnexIcons.Squat
                in pull -> FitnexIcons.Arm
                else -> FitnexIcons.Workout
            }
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(Radii.md)).background(colors.accentMuted), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                // The whole name first (two lines at most); the badges go under it so they never cut it.
                Text(
                    routine.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (routine.aiRationale != null) {
                    Row(Modifier.padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                        Badge(stringResource(R.string.ai_routine_badge))
                    }
                }
                val subtitle = if (last != null && lastDay != null) {
                    val minutes = last.duration.toMinutes().toInt()
                    when (lastDay) {
                        today -> stringResource(R.string.last_today, minutes)
                        today.minusDays(1) -> stringResource(R.string.last_yesterday, minutes)
                        else -> stringResource(
                            R.string.last_date,
                            lastDay.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", locale)).replaceFirstChar { it.titlecase(locale) },
                            minutes,
                        )
                    }
                } else {
                    pluralStringResource(R.plurals.workout_exercise_count, routine.exercises.size, routine.exercises.size) +
                        " · " + (if (routine.days.isEmpty()) stringResource(R.string.workout_no_days) else daysLabel(routine.days))
                }
                Text(subtitle, style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1)
            }
            IconButton(onClick = onStart, modifier = Modifier.testTag("start_${routine.name}")) {
                if (doneToday) {
                    Box(Modifier.size(36.dp).background(colors.success, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.live_start_cd, routine.name), tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(22.dp))
                    }
                } else {
                    Box(
                        Modifier.size(36.dp).border(2.dp, colors.textSecondary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.live_start_cd, routine.name), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textSecondary)
        }
    }
}

/** Weekly goal (finished workouts this week vs target), same numbers as Progreso. */
@Composable
private fun ConsistencyCard(done: Int, target: Int) {
    val colors = FitnexTheme.colors
    GlassCard(Modifier.fillMaxWidth().testTag("consistency_card"), contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FitnexIcons.Trophy, null, tint = colors.accent, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(stringResource(R.string.consistency_title), style = FitnexTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.textPrimary)
                Text(pluralStringResource(R.plurals.consistency_body, target, target), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.size(width = 96.dp, height = 40.dp)) {
                Text("$done / $target", style = FitnexTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = colors.accent, modifier = Modifier.testTag("consistency_count"))
                val p = (done.toFloat() / target).coerceIn(0f, 1f)
                Box(Modifier.padding(top = Spacing.xs).fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.accentMuted)) {
                    Box(Modifier.fillMaxWidth(p).height(8.dp).clip(CircleShape).background(colors.accent))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Library

private data class QuickChip(val key: String, val label: String, val selected: Boolean, val toggle: () -> Unit)

/**
 * Exercise library: a search bar that stays on top while scrolling, quick toggles, muscle-group
 * tiles with their exercise count, and a compact list grouped by initial in glass blocks (about
 * twice as many exercises per screen as single cards).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ExerciseLibraryScreen(
    state: LibraryUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onFilter: ((com.fitnex.feature.workout.library.ExerciseFilter) -> com.fitnex.feature.workout.library.ExerciseFilter) -> Unit,
    onClearFilters: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onConfirmSelection: (List<String>) -> Unit,
) {
    val colors = FitnexTheme.colors
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val f = state.filter

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (showCreate) {
            com.fitnex.feature.workout.library.CustomExerciseSheet(
                onDismiss = { showCreate = false },
                // A new exercise is selected straight away when choosing for a routine; otherwise its page opens.
                onCreated = { id -> showCreate = false; if (state.pickMode) onToggleSelection(id) else onOpenExercise(id) },
            )
        }
        // Alphabetical sections only when browsing; a search keeps its relevance order.
        val sections = remember(state.results, f.query) {
            if (f.query.isNotBlank()) listOf("" to state.results)
            else state.results.groupBy { com.fitnex.feature.workout.library.ExerciseFilter.normalize(it.exercise.name).take(1).uppercase() }.toList()
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("library_list"),
            contentPadding = PaddingValues(bottom = if (state.pickMode) Spacing.lg else contentPadding.calculateBottomPadding()),
        ) {
            // The big title scrolls away with the list (it took a third of the screen when fixed);
            // the search bar then stays on top while the list scrolls under it.
            item(key = "header") {
                FitnexHeader(
                    title = stringResource(if (state.pickMode) R.string.library_pick_title else R.string.workout_library_title),
                    overline = stringResource(R.string.library_overline),
                    onBack = onBack,
                    actions = {
                        com.fitnex.core.designsystem.component.GlassIconButton(
                            Icons.Rounded.Add, stringResource(R.string.exercise_custom_title), { showCreate = true }, Modifier.testTag("custom_create"),
                        )
                    },
                )
            }
            stickyHeader(key = "search") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(colors.background, colors.background, colors.background.copy(alpha = 0f))))
                        .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.md)
                        // TalkBack reads the search first, even though a sticky header is placed last.
                        .semantics { isTraversalGroup = true; traversalIndex = -1f },
                ) {
                    FitnexTextField(
                        value = f.query,
                        onValueChange = { q -> onFilter { it.copy(query = q) } },
                        label = stringResource(R.string.library_search),
                        trailingIcon = {
                            if (f.query.isNotEmpty()) {
                                IconButton(onClick = { onFilter { it.copy(query = "") } }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.library_search_clear), tint = colors.textSecondary)
                                }
                            } else {
                                Icon(Icons.Rounded.Search, null, tint = colors.textSecondary)
                            }
                        },
                        modifier = Modifier.testTag("library_search"),
                    )
                }
            }
            item(key = "quick") {
                val quick = buildList {
                    add(QuickChip("fav", stringResource(R.string.library_favorites), f.favoritesOnly) { onFilter { it.copy(favoritesOnly = !it.favoritesOnly) } })
                    add(QuickChip("mine", stringResource(R.string.library_my_equipment), f.onlyMyEquipment) { onFilter { it.copy(onlyMyEquipment = !it.onlyMyEquipment) } })
                    val filtersLabel = stringResource(R.string.library_more_filters) + if (f.sheetFilterCount > 0) " (${f.sheetFilterCount})" else ""
                    add(QuickChip("more", filtersLabel, f.sheetFilterCount > 0) { showFilters = true })
                    listOf(ExerciseType.FUNCTIONAL, ExerciseType.CARDIO, ExerciseType.MOBILITY, ExerciseType.STRETCHING).forEach { t ->
                        add(QuickChip(t.name, if (t == ExerciseType.FUNCTIONAL) stringResource(R.string.group_full_body) else t.label(), t in f.types) { onFilter { it.copy(types = if (t in it.types) it.types - t else it.types + t) } })
                    }
                }
                FitnexChipGroup(
                    options = quick, isSelected = { it.selected }, onToggle = { it.toggle() }, label = { it.label },
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).testTag("library_chips"),
                )
            }
            item(key = "groups") {
                MuscleGroupTiles(state.groupCounts, f.groups) { g -> onFilter { it.copy(groups = if (g in it.groups) it.groups - g else it.groups + g) } }
            }
            item(key = "count") {
                Row(Modifier.padding(horizontal = Spacing.gutter).padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.library_results, state.results.size, state.results.size),
                        style = FitnexTheme.typography.labelMedium, color = colors.textSecondary,
                        modifier = Modifier.weight(1f).testTag("library_count"),
                    )
                    if (f.isActive) {
                        TextButton(onClick = onClearFilters) { Text(stringResource(R.string.library_clear), color = colors.accent) }
                    }
                }
            }
            when {
                state.loading -> item { LoadingState() }
                state.results.isEmpty() -> item {
                    GlassCard(Modifier.padding(horizontal = Spacing.gutter)) {
                        MessageState(
                            title = stringResource(R.string.library_empty_title),
                            message = stringResource(R.string.library_empty_message),
                            actionLabel = stringResource(R.string.library_clear).takeIf { f.isActive },
                            onAction = { onClearFilters(); onFilter { it.copy(query = "") } },
                        )
                    }
                }
                else -> sections.forEach { (letter, rows) ->
                    if (letter.isNotEmpty()) {
                        item(key = "letter_$letter") {
                            Text(
                                letter, style = FitnexTheme.typography.labelLarge, color = colors.accent,
                                modifier = Modifier.padding(start = Spacing.gutter + Spacing.xs, top = Spacing.md, bottom = Spacing.xs).semantics { heading() },
                            )
                        }
                    }
                    itemsIndexed(rows, key = { _, it -> it.exercise.id }) { i, item ->
                        ExerciseRow(
                            item = item,
                            first = i == 0,
                            last = i == rows.lastIndex,
                            pickMode = state.pickMode,
                            selected = item.exercise.id in state.selected,
                            onClick = { if (state.pickMode) onToggleSelection(item.exercise.id) else onOpenExercise(item.exercise.id) },
                        )
                    }
                }
            }
        }
        if (state.pickMode) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter)) {
                FitnexPrimaryButton(
                    stringResource(R.string.library_add_selected, state.selected.size),
                    onClick = { onConfirmSelection(state.selected) },
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("library_confirm"),
                )
            }
        }
    }

    if (showFilters) {
        FilterSheet(filter = f, onFilter = onFilter, onClear = onClearFilters, onDismiss = { showFilters = false })
    }
}

@Composable
private fun FilterSheet(
    filter: com.fitnex.feature.workout.library.ExerciseFilter,
    onFilter: ((com.fitnex.feature.workout.library.ExerciseFilter) -> com.fitnex.feature.workout.library.ExerciseFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val eqLabels = Equipment.entries.associateWith { it.label() }
    val typeLabels = ExerciseType.entries.associateWith { it.label() }
    val diffLabels = Difficulty.entries.associateWith { it.label() }
    FitnexBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.library_filters_title)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeader(stringResource(R.string.library_equipment))
            FitnexChipGroup(
                Equipment.entries, { it in filter.equipment },
                { e -> onFilter { it.copy(equipment = if (e in it.equipment) it.equipment - e else it.equipment + e) } },
                { eqLabels.getValue(it) },
            )
            SectionHeader(stringResource(R.string.library_type))
            FitnexChipGroup(
                ExerciseType.entries.filter { it != ExerciseType.CARDIO }, { it in filter.types },
                { t -> onFilter { it.copy(types = if (t in it.types) it.types - t else it.types + t) } },
                { typeLabels.getValue(it) },
            )
            SectionHeader(stringResource(R.string.library_level))
            FitnexChipGroup(
                Difficulty.entries, { it in filter.difficulties },
                { d -> onFilter { it.copy(difficulties = if (d in it.difficulties) it.difficulties - d else it.difficulties + d) } },
                { diffLabels.getValue(it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FitnexSecondaryButton(stringResource(R.string.library_clear), onClear, Modifier.weight(1f))
                FitnexPrimaryButton(stringResource(com.fitnex.core.designsystem.R.string.ds_picker_done), onDismiss, Modifier.weight(1f))
            }
        }
    }
}

/** Muscle groups as compact glass pills with how many exercises each has; tap to filter. */
@Composable
private fun MuscleGroupTiles(
    counts: Map<com.fitnex.feature.workout.library.MuscleGroup, Int>,
    selected: Set<com.fitnex.feature.workout.library.MuscleGroup>,
    onToggle: (com.fitnex.feature.workout.library.MuscleGroup) -> Unit,
) {
    val colors = FitnexTheme.colors
    val selectedLabel = stringResource(R.string.library_selected)
    Row(
        Modifier.padding(top = Spacing.sm).horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).testTag("library_groups"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        com.fitnex.feature.workout.library.MuscleGroup.entries.forEach { g ->
            val on = g in selected
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .height(38.dp)
                    .clip(shape)
                    .background(if (on) colors.accent else colors.surface.copy(alpha = if (colors.isDark) 0.6f else 1f))
                    .liquidRim(colors, shape)
                    .clickable(role = Role.Checkbox, onClick = { onToggle(g) })
                    .semantics { if (on) stateDescription = selectedLabel }
                    .padding(horizontal = Spacing.md)
                    .testTag("group_${g.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g.label(), style = FitnexTheme.typography.labelLarge, color = if (on) colors.onAccent else colors.textPrimary, maxLines = 1)
                Text(
                    "  ${counts[g] ?: 0}",
                    style = FitnexTheme.typography.labelMedium, color = if (on) colors.onAccent.copy(alpha = 0.8f) else colors.textSecondary, maxLines = 1,
                )
            }
        }
    }
}
/** What kind of exercise it is, for rows without a photo (so the list isn't one repeated icon). */
internal fun exerciseIcon(e: Exercise): androidx.compose.ui.graphics.vector.ImageVector = when {
    e.type == ExerciseType.CARDIO -> FitnexIcons.Flame
    e.type == ExerciseType.MOBILITY || e.type == ExerciseType.STRETCHING -> FitnexIcons.Body
    Equipment.KETTLEBELL in e.equipment -> FitnexIcons.Kettlebell
    Equipment.MACHINE in e.equipment || Equipment.CABLE in e.equipment || Equipment.CARDIO_MACHINE in e.equipment -> FitnexIcons.Layers
    e.equipment.isEmpty() || e.equipment.all { it == Equipment.BODYWEIGHT || it == Equipment.PULL_UP_BAR || it == Equipment.DIP_BARS } -> FitnexIcons.Squat
    Equipment.RESISTANCE_BAND in e.equipment -> FitnexIcons.Repeat
    else -> FitnexIcons.Workout
}

/** One compact row of a glass block: rounded only at the block's top and bottom ends. */
@Composable
private fun ExerciseRow(item: ExerciseListItem, first: Boolean, last: Boolean, pickMode: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = FitnexTheme.colors
    val e = item.exercise
    val selectedLabel = stringResource(R.string.library_selected)
    val r = Radii.lg
    val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
    Column(
        Modifier
            .padding(horizontal = Spacing.gutter)
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = if (colors.isDark) 0.62f else 1f))
            .clickable(onClick = onClick)
            .testTag("exercise_${e.id}")
            .semantics {
                if (pickMode) {
                    role = Role.Checkbox
                    if (selected) stateDescription = selectedLabel
                }
            },
    ) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            com.fitnex.feature.workout.media.ExerciseThumbnail(e.mediaUri, size = 44.dp, placeholder = exerciseIcon(e))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(e.name, style = FitnexTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        val muscles = item.primaryMuscles.joinToString(", ") { it.name }
                        if (muscles.isNotEmpty()) {
                            withStyle(androidx.compose.ui.text.SpanStyle(color = colors.accent)) { append(muscles) }
                            append(" · ")
                        }
                        append((e.equipment.map { it.label() } + e.difficulty.label()).joinToString(" · "))
                    },
                    style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            when {
                pickMode && selected -> Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent)
                pickMode -> Box(Modifier.size(22.dp).border(1.5.dp, colors.textDisabled, CircleShape))
                else -> {
                    if (item.isFavorite) Icon(Icons.Rounded.Favorite, null, tint = colors.accent, modifier = Modifier.padding(end = Spacing.xs).size(16.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textDisabled, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (!last) HorizontalDivider(color = colors.divider, modifier = Modifier.padding(start = Spacing.md + 44.dp + Spacing.md))
    }
}

// ---------------------------------------------------------------- Detail

@Composable
internal fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMuscle: (String) -> Unit,
    onDelete: () -> Unit = {},
) {
    val colors = FitnexTheme.colors
    val e = state.exercise
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        com.fitnex.core.designsystem.component.FitnexConfirmDialog(
            title = stringResource(R.string.exercise_custom_delete),
            message = stringResource(R.string.exercise_custom_delete_body),
            confirmLabel = stringResource(R.string.exercise_custom_delete_confirm),
            dismissLabel = stringResource(R.string.exercise_custom_delete_cancel),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        FitnexHeader(
            title = e?.name ?: "",
            overline = e?.type?.label(),
            onBack = onBack,
            actions = {
                if (e != null && e.isCustom) {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("detail_delete")) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.exercise_custom_delete), tint = colors.error)
                    }
                }
                if (e != null) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("detail_favorite")) {
                        Icon(
                            if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(if (state.isFavorite) R.string.library_favorite_on else R.string.library_favorite_off),
                            tint = if (state.isFavorite) colors.accent else colors.textPrimary,
                        )
                    }
                }
            },
        )
        when {
            state.loading -> LoadingState()
            e == null -> MessageState(title = "", message = stringResource(R.string.detail_not_found))
            else -> ExerciseDetailContent(e, state, onOpenMuscle)
        }
    }
}

@Composable
private fun roleLabel(i: MuscleInvolvement): Int = when (i) {
    MuscleInvolvement.PRIMARY -> R.string.detail_role_primary
    MuscleInvolvement.SECONDARY -> R.string.detail_role_secondary
    MuscleInvolvement.STABILIZER -> R.string.detail_role_stabilizer
}

@Composable
private fun ExerciseDetailContent(e: Exercise, state: ExerciseDetailUiState, onOpenMuscle: (String) -> Unit) {
    val colors = FitnexTheme.colors
    Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val media = e.mediaUri
        if (media != null) {
            // Real photos (public domain): start and end position.
            com.fitnex.feature.workout.media.ExerciseMotion(media, stringResource(R.string.media_cd, e.name))
        } else {
            // No photos (custom exercises, and catalogue entries still without images): say so instead of a stock/fake picture.
            GlassCard(Modifier.fillMaxWidth().testTag("detail_media_placeholder")) {
                Column(Modifier.fillMaxWidth().height(120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(FitnexIcons.Workout, null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(Spacing.sm))
                    Text(stringResource(if (e.isCustom) R.string.detail_no_media else R.string.detail_no_media_catalog), style = FitnexTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
        Text(e.description, style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Badge(e.difficulty.label())
            e.equipment.forEach { Badge(it.label()) }
        }

        // Spec §5: the map highlights what this exercise works (stronger red = bigger role).
        val roles = e.muscles.associate { it.muscleId to it.involvement }
        MuscleMapCard(
            levels = roles.mapValues { it.value.weight },
            muscles = state.muscles,
            info = { id -> stringResource(roles[id]?.let { roleLabel(it) } ?: R.string.detail_role_none) },
            onOpenMuscle = onOpenMuscle,
            title = stringResource(R.string.detail_muscles),
        )
        GlassCard(Modifier.fillMaxWidth()) {
            val primary = e.muscles.filter { it.involvement == MuscleInvolvement.PRIMARY }.mapNotNull { state.muscles[it.muscleId] }
            val others = e.muscles.filter { it.involvement != MuscleInvolvement.PRIMARY }.mapNotNull { state.muscles[it.muscleId] }
            Text(stringResource(R.string.detail_primary), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary)
            primary.forEach { m ->
                Column(Modifier.fillMaxWidth().clickable { onOpenMuscle(m.id) }.testTag("detail_muscle_${m.id}")) {
                    Text("● " + m.name, style = FitnexTheme.typography.titleMedium, color = colors.accent)
                    Text(m.function, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.lg, bottom = Spacing.xs))
                }
            }
            if (others.isNotEmpty()) {
                Text(
                    stringResource(R.string.detail_secondary), style = FitnexTheme.typography.labelMedium, color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                others.forEach { m ->
                    Text(
                        m.name, style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenMuscle(m.id) }.padding(vertical = Spacing.xs).testTag("detail_muscle_${m.id}"),
                    )
                }
            }
        }

        NumberedSection(stringResource(R.string.detail_how), e.instructions, numbered = true)
        if (e.techniqueTips.isNotEmpty()) NumberedSection(stringResource(R.string.detail_tips), e.techniqueTips, numbered = false)
        if (e.commonMistakes.isNotEmpty()) NumberedSection(stringResource(R.string.detail_mistakes), e.commonMistakes, numbered = false, warning = true)
    }
}

@Composable
private fun NumberedSection(title: String, lines: List<String>, numbered: Boolean, warning: Boolean = false) {
    val colors = FitnexTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        lines.forEachIndexed { i, line ->
            Row(Modifier.padding(vertical = Spacing.xs)) {
                Text(
                    if (numbered) "${i + 1}." else if (warning) "✕" else "✓",
                    style = FitnexTheme.typography.titleMedium,
                    color = if (warning) colors.warning else colors.accent,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
                Text(line, style = FitnexTheme.typography.bodyMedium, color = colors.textPrimary)
            }
        }
    }
}

// ---------------------------------------------------------------- Shared bits

@Composable
internal fun Badge(text: String) {
    Text(
        text,
        style = FitnexTheme.typography.labelSmall,
        color = FitnexTheme.colors.accent,
        modifier = Modifier
            .background(FitnexTheme.colors.accent.copy(alpha = 0.14f), RoundedCornerShape(Radii.pill))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

@Composable
internal fun IconBubble(content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).background(FitnexTheme.colors.accent.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
