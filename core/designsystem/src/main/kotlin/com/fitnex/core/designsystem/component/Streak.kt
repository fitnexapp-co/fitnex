package com.fitnex.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import com.fitnex.core.model.DayStreak
import com.fitnex.core.model.StreakState
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Flame orange: the streak's own colour, whatever the accent (like trophy gold). */
val StreakOrange = Color(0xFFF26A1B)
private val StreakAmber = Color(0xFFFFB45C)

/** A filled two-tone flame drawn in code (no image asset), with a soft glow behind it. */
@Composable
fun StreakFlame(size: Dp, modifier: Modifier = Modifier, lit: Boolean = true, glow: Boolean = true) {
    val outer = if (lit) StreakOrange else FitnexTheme.colors.textDisabled
    val inner = if (lit) StreakAmber else FitnexTheme.colors.textDisabled.copy(alpha = 0.5f)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (glow && lit) drawCircle(Brush.radialGradient(listOf(StreakOrange.copy(alpha = 0.45f), Color.Transparent)), radius = w * 0.75f, center = Offset(w / 2, h * 0.62f))
        // Drawn on a 100 × 100 grid: a tall body with its tip leaning right, a small flick on the left,
        // and a bright inner core near the base.
        fun p(x: Float, y: Float) = Offset(w * x / 100f, h * y / 100f)
        fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val a = p(x1, y1); val b = p(x2, y2); val e = p(x3, y3)
            cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
        }
        val body = Path().apply {
            p(52f, 3f).let { moveTo(it.x, it.y) }
            c(58f, 22f, 80f, 32f, 81f, 58f)
            c(82f, 82f, 67f, 98f, 50f, 98f)
            c(32f, 98f, 18f, 84f, 19f, 65f)
            c(20f, 52f, 27f, 44f, 34f, 37f)
            c(34f, 47f, 38f, 53f, 44f, 55f)
            c(40f, 38f, 45f, 19f, 52f, 3f)
            close()
        }
        val core = Path().apply {
            p(53f, 50f).let { moveTo(it.x, it.y) }
            c(57f, 63f, 67f, 69f, 66f, 81f)
            c(65f, 91f, 58f, 95f, 50f, 95f)
            c(42f, 95f, 34f, 90f, 34f, 80f)
            c(34f, 71f, 41f, 65f, 44f, 59f)
            c(45f, 65f, 47f, 68f, 50f, 69f)
            c(49f, 62f, 50f, 56f, 53f, 50f)
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(if (lit) Color(0xFFFF8A3D) else outer, outer, if (lit) Color(0xFFE5481A) else outer)))
        drawPath(core, inner)
    }
}

/** Compact streak chip for headers: flame + number. */
@Composable
fun StreakChip(streak: DayStreak, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FitnexTheme.colors
    val desc = pluralStringResource(R.plurals.ds_streak_cd, streak.current, streak.current)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surface.copy(alpha = if (colors.isDark) 0.7f else 1f))
            .liquidRim(colors, RoundedCornerShape(50))
            .clickable(onClickLabel = stringResource(R.string.ds_streak_open), onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .padding(start = Spacing.sm, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs)
            .testTag("streak_chip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakFlame(26.dp, lit = streak.current > 0, glow = false)
        Text(
            streak.current.toString(),
            style = FitnexTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (streak.current > 0) StreakOrange else colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
    }
}

/**
 * Full-screen streak moment: the flame with the day count, this week's days, one line saying what
 * to do next, how it works, and Continue. Shown after a workout and from the Progress header.
 */
@Composable
fun StreakDialog(streak: DayStreak, onDismiss: () -> Unit) {
    val colors = FitnexTheme.colors
    val locale = rememberAppLocale()
    var showHow by rememberSaveable { mutableStateOf(false) }
    val reduce = FitnexTheme.appearance.reduceMotion
    // Entrance: the flame grows in with one light bounce (a celebration, but a sober one), then
    // breathes twice and stays still, so it never competes with "Continuar".
    val pop = remember { Animatable(if (reduce) 1f else 0.85f) }
    val breath = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 300f))
        repeat(2) {
            breath.animateTo(1.05f, tween(1400, easing = FastOutSlowInEasing))
            breath.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
    }
    val breathe = breath.value

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Edge to edge: the dark background also goes behind the status and navigation bars.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF151313), Color(0xFF0B0B0B))))
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("streak_dialog"),
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    stringResource(R.string.ds_streak_title), style = FitnexTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.weight(1f))
                val desc = pluralStringResource(R.plurals.ds_streak_cd, streak.current, streak.current)
                Row(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = desc },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StreakFlame(112.dp, Modifier.scale(pop.value * breathe), lit = streak.current > 0)
                    Text(
                        streak.current.toString(),
                        style = FitnexTheme.typography.displayLarge.copy(fontSize = 84.sp, fontWeight = FontWeight.Medium),
                        color = if (streak.current > 0) StreakOrange else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = Spacing.sm).testTag("streak_count"),
                    )
                }
                Text(
                    pluralStringResource(R.plurals.ds_streak_days, streak.current, streak.current),
                    style = FitnexTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(Spacing.xl))
                // This week, Monday first: trained days glow, today is outlined.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DayOfWeek.entries.forEach { d ->
                        val done = d in streak.weekTrained
                        val isToday = d == streak.today
                        val future = d.value > streak.today.value
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                d.getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale),
                                style = FitnexTheme.typography.labelSmall, color = Color.White.copy(alpha = if (future) 0.4f else 0.7f),
                            )
                            Box(
                                Modifier
                                    .padding(top = Spacing.xs)
                                    .size(34.dp)
                                    .then(if (done) Modifier.drawBehind { drawCircle(StreakOrange.copy(alpha = 0.35f), radius = size.minDimension * 0.75f) } else Modifier)
                                    .clip(CircleShape)
                                    .background(if (done) StreakOrange else Color.White.copy(alpha = 0.06f))
                                    .border(if (isToday && !done) 1.5.dp else 1.dp, if (isToday && !done) StreakOrange else Color.White.copy(alpha = 0.12f), CircleShape)
                                    .testTag("streak_day_${d.value}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    stringResource(
                        when (streak.state) {
                            StreakState.FED_TODAY -> R.string.ds_streak_fed
                            StreakState.CAN_GROW -> R.string.ds_streak_can_grow
                            StreakState.LAST_DAY -> R.string.ds_streak_last_day
                            StreakState.NONE -> R.string.ds_streak_none
                        },
                    ),
                    style = FitnexTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("streak_message"),
                )
                if (streak.best > streak.current) {
                    Text(
                        pluralStringResource(R.plurals.ds_streak_best, streak.best, streak.best),
                        style = FitnexTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showHow = !showHow }, modifier = Modifier.testTag("streak_how")) {
                    Text(stringResource(R.string.ds_streak_how), color = Color.White.copy(alpha = 0.75f), style = FitnexTheme.typography.bodyMedium)
                }
                if (showHow) {
                    Text(
                        stringResource(R.string.ds_streak_how_body),
                        style = FitnexTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = Spacing.md).testTag("streak_how_body"),
                    )
                }
                // White pill, dark text: the one way out.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF1F0EC))
                        .clickable(onClick = onDismiss)
                        .testTag("streak_continue"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.ds_streak_continue), style = FitnexTheme.typography.labelLarge, color = Color(0xFF111114))
                }
            }
        }
    }
}
