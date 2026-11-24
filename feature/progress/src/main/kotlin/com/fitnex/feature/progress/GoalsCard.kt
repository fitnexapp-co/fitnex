package com.fitnex.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.component.FitnexBottomSheet
import com.fitnex.core.designsystem.component.FitnexProgressRing
import com.fitnex.core.designsystem.component.FitnexSegmentedControl
import com.fitnex.core.designsystem.component.CardEmphasis
import com.fitnex.core.designsystem.component.GlassCard
import com.fitnex.core.designsystem.component.LiquidGlassSurface
import com.fitnex.core.designsystem.component.StaggeredReveal
import com.fitnex.core.designsystem.icon.FitnexIcons
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.Achievements
import com.fitnex.core.model.Badge
import com.fitnex.core.model.BadgeProgress
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Trophy gold: the one fixed colour, so "goal" reads at a glance whatever the accent. */
private val Gold = Color(0xFFF5B83D)

/** "Objetivo semanal" and "Tus logros", styled like the FITNEX dashboard. */
@Composable
internal fun GoalsCard(a: Achievements, onSetGoal: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        WeeklyGoalCard(a, onSetGoal)
        AchievementsSection(a)
    }
}

@Composable
private fun WeeklyGoalCard(a: Achievements, onSetGoal: (Int) -> Unit) {
    val colors = FitnexTheme.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth().testTag("goals_card"), emphasis = CardEmphasis.Liquid) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(FitnexIcons.Trophy, contentDescription = null, tint = Gold, modifier = Modifier.size(28.dp))
            Text(
                stringResource(R.string.goal_title),
                style = FitnexTheme.typography.titleLarge, color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = Spacing.md),
            )
            // Quiet pill, not a second primary action.
            Text(
                stringResource(R.string.goal_change),
                style = FitnexTheme.typography.labelLarge, color = colors.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.6f))
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { editing = true }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm + Spacing.xxs)
                    .testTag("goal_edit"),
            )
        }
        Row(Modifier.padding(top = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            FitnexProgressRing(
                progress = a.weekProgress, size = 112.dp, strokeWidth = 12.dp,
                accessibilityLabel = stringResource(R.string.goal_a11y, a.weekDone, a.weekTarget),
            ) {
                StaggeredReveal(index = 1) {
                    Text("${a.weekDone}/${a.weekTarget}", style = FitnexTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                }
            }
            Column(Modifier.weight(1f).padding(start = Spacing.lg)) {
                StaggeredReveal(index = 2) {
                    Text(
                        if (a.weekDone >= a.weekTarget) stringResource(R.string.goal_done)
                        else pluralStringResource(R.plurals.goal_left, a.weekTarget - a.weekDone, a.weekTarget - a.weekDone),
                        style = FitnexTheme.typography.bodyLarge, color = colors.textPrimary,
                    )
                }
                Text(
                    if (a.streakWeeks > 0) pluralStringResource(R.plurals.goal_streak, a.streakWeeks, a.streakWeeks) else stringResource(R.string.goal_no_streak),
                    style = FitnexTheme.typography.bodySmall, color = colors.accent, modifier = Modifier.padding(top = Spacing.xs).testTag("goal_streak"),
                )
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = Spacing.md))
                WeekDays(a.trainedDays, a.today)
            }
        }
    }
    if (editing) {
        FitnexBottomSheet(onDismiss = { editing = false }, title = stringResource(R.string.goal_sheet_title)) {
            Text(stringResource(R.string.goal_sheet_body), style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = Spacing.md))
            FitnexSegmentedControl((1..7).toList(), a.weekTarget, { onSetGoal(it); editing = false }, { it.toString() }, Modifier.testTag("goal_picker"))
        }
    }
}

/** L M X J V S D: trained days filled, today outlined. */
@Composable
private fun WeekDays(trained: Set<DayOfWeek>, today: DayOfWeek) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val done = d in trained
            val isToday = d == today
            val name = d.getDisplayName(TextStyle.FULL, locale)
            val desc = stringResource(if (done) R.string.goal_day_done else R.string.goal_day_pending, name)
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (done) colors.accent else if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f))
                    .border(if (isToday) 1.5.dp else 0.dp, if (isToday) colors.accent else Color.Transparent, CircleShape)
                    .testTag("goal_day_${d.value}")
                    .clearAndSetSemantics { contentDescription = desc }, // "lunes: entrenado", not also "L"
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = FitnexTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (done) colors.onAccent else if (isToday) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsSection(a: Achievements) {
    val colors = FitnexTheme.colors
    var showAll by rememberSaveable { mutableStateOf(false) }
    // Unlocked first, then the closest to unlocking.
    val ordered = a.badges.sortedWith(compareByDescending<BadgeProgress> { it.unlocked }.thenByDescending { it.fraction })
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("achievements")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.achievements_title), style = FitnexTheme.typography.titleLarge, color = colors.textPrimary)
            Text(
                "  ${a.unlocked.size}/${a.badges.size}",
                style = FitnexTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAll = true }, modifier = Modifier.testTag("achievements_all")) {
                Text(stringResource(R.string.achievements_all), style = FitnexTheme.typography.labelLarge, color = colors.textPrimary)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.padding(start = Spacing.xs).size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
            ordered.take(4).forEachIndexed { i, b -> StaggeredReveal(index = 3 + i) { BadgeItem(b) } }
        }
    }
    if (showAll) {
        FitnexBottomSheet(onDismiss = { showAll = false }, title = stringResource(R.string.badges_title, a.unlocked.size, a.badges.size)) {
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                maxItemsInEachRow = 3,
            ) { ordered.forEach { BadgeItem(it) } }
        }
    }
}

/** Hexagon with a flat top, like a medal. */
private val Hexagon = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

@Composable
private fun BadgeItem(b: BadgeProgress) {
    val colors = FitnexTheme.colors
    val name = stringResource(b.badge.labelRes())
    val desc = if (b.unlocked) name else stringResource(R.string.badge_locked_a11y, name, b.current.coerceAtMost(b.badge.target), b.badge.target)
    Column(
        Modifier.width(78.dp).testTag("badge_${b.badge.name}").clearAndSetSemantics { contentDescription = desc }, // name read once
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fill = if (b.unlocked) {
            Brush.verticalGradient(listOf(lerp(colors.accent, Color.Black, 0.35f), lerp(colors.accent, Color.Black, 0.75f)))
        } else {
            // Light theme: a soft ink tint, so locked medals stay visible on the pale background.
            if (colors.isDark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f)))
            else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.04f)))
        }
        // Liquid-glass medal: diagonal rim light, light sweep and spring press.
        LiquidGlassSurface(
            modifier = Modifier
                .size(width = 60.dp, height = 66.dp)
                .then(if (b.unlocked) Modifier.shadow(14.dp, Hexagon, ambientColor = colors.accent, spotColor = colors.accent) else Modifier),
            shape = Hexagon,
            fill = fill,
            elevation = 0.dp,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (b.unlocked) Modifier.border(2.dp, colors.accentStrong.copy(alpha = 0.7f), Hexagon) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val tint = when {
                    !b.unlocked -> colors.textDisabled
                    b.badge == Badge.WORKOUTS_100 -> Gold
                    else -> Color.White
                }
                Icon(b.badge.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            name, style = FitnexTheme.typography.labelMedium, color = if (b.unlocked) colors.textPrimary else colors.textSecondary,
            textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = Spacing.sm),
        )
        if (!b.unlocked) {
            Text("${b.current.coerceAtMost(b.badge.target)}/${b.badge.target}", style = FitnexTheme.typography.labelSmall, color = colors.textDisabled)
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
}

private fun Badge.icon() = when (this) {
    Badge.FIRST_WORKOUT -> FitnexIcons.Workout
    Badge.WORKOUTS_10 -> FitnexIcons.Flame
    Badge.WORKOUTS_50 -> Icons.Rounded.Star
    Badge.WORKOUTS_100 -> FitnexIcons.Trophy
    Badge.STREAK_4_WEEKS, Badge.STREAK_12_WEEKS -> FitnexIcons.Calendar
    Badge.SETS_500 -> FitnexIcons.Arm
    Badge.HOURS_10 -> FitnexIcons.Progress
    Badge.VARIETY_15 -> FitnexIcons.Sparkle
}

private fun Badge.labelRes() = when (this) {
    Badge.FIRST_WORKOUT -> R.string.badge_first
    Badge.WORKOUTS_10 -> R.string.badge_10
    Badge.WORKOUTS_50 -> R.string.badge_50
    Badge.WORKOUTS_100 -> R.string.badge_100
    Badge.STREAK_4_WEEKS -> R.string.badge_streak4
    Badge.STREAK_12_WEEKS -> R.string.badge_streak12
    Badge.SETS_500 -> R.string.badge_sets
    Badge.HOURS_10 -> R.string.badge_hours
    Badge.VARIETY_15 -> R.string.badge_variety
}
