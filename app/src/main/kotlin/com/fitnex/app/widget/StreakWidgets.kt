package com.fitnex.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fitnex.app.MainActivity
import com.fitnex.app.R
import com.fitnex.core.model.DayStreakCalculator
import com.fitnex.core.model.StreakState
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DateStyle
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.first

/** Daily streak and this week's numbers, all from finished workouts on this device. */
internal data class StreakWidgetData(
    val signedIn: Boolean,
    val streak: Int = 0,
    val best: Int = 0,
    val state: StreakState = StreakState.NONE,
    val weekTrained: Set<DayOfWeek> = emptySet(),
    val today: DayOfWeek = DayOfWeek.MONDAY,
    val weekWorkouts: Int = 0,
    val weekMinutes: Long = 0,
    val weekSets: Int = 0,
    val totalWorkouts: Int = 0,
)

internal suspend fun loadStreakData(context: Context): StreakWidgetData {
    val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val uid = ep.session().currentUserId.first() ?: return StreakWidgetData(signedIn = false)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val done = ep.stats().observeCompletedSessions(uid).first()
    val streak = DayStreakCalculator.compute(done.map { it.startedAt.atZone(zone).toLocalDate() }, today)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = done.filter { !it.startedAt.atZone(zone).toLocalDate().isBefore(monday) }
    return StreakWidgetData(
        signedIn = true,
        streak = streak.current,
        best = streak.best,
        state = streak.state,
        weekTrained = streak.weekTrained,
        today = streak.today,
        weekWorkouts = week.size,
        weekMinutes = week.sumOf { it.duration.toMinutes() },
        weekSets = week.sumOf { it.sets.size },
        totalWorkouts = done.size,
    )
}

private val Bg = Color(0xFF141417)
private val White = ColorProvider(Color(0xFFF5F5F7))
private val Grey = ColorProvider(Color(0xFF9A9AA3))
private val Orange = Color(0xFFF26A1B)
private val DotOff = Color(0xFF2A2A30)

@Composable
private fun Flame(lit: Boolean, size: Int) {
    Image(
        ImageProvider(if (lit) R.drawable.ic_widget_flame else R.drawable.ic_widget_flame_off),
        contentDescription = null,
        modifier = GlanceModifier.size(size.dp),
    )
}

@Composable
private fun StreakHeadline(context: Context, d: StreakWidgetData, numberSp: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Flame(d.streak > 0, numberSp + 6)
        Spacer(GlanceModifier.width(6.dp))
        Column {
            Text(d.streak.toString(), style = TextStyle(color = White, fontSize = numberSp.sp, fontWeight = FontWeight.Bold))
            Text(
                context.resources.getQuantityString(R.plurals.widget_streak_days, d.streak),
                style = TextStyle(color = Grey, fontSize = 11.sp),
            )
        }
    }
}

/** Monday–Sunday: orange when you trained, an outline for today. */
@Composable
private fun WeekDots(context: Context, d: StreakWidgetData, dot: Int) {
    val locale = context.resources.configuration.locales[0]
    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Glance rows take at most 10 children, so the gap is padding, not Spacers.
        DayOfWeek.entries.forEach { day ->
            val trained = day in d.weekTrained
            val isToday = day == d.today
            Box(GlanceModifier.padding(horizontal = 2.dp)) {
            Box(
                GlanceModifier.size(dot.dp).cornerRadius((dot / 2).dp)
                    .background(if (trained) Orange else if (isToday) Color(0xFF4A3A8C) else DotOff),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(DateStyle.NARROW, locale).uppercase(locale),
                    style = TextStyle(
                        color = if (trained || isToday) White else Grey,
                        fontSize = 10.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            }
        }
    }
}

private fun message(context: Context, d: StreakWidgetData): String = context.getString(
    when (d.state) {
        StreakState.FED_TODAY -> R.string.widget_streak_fed
        StreakState.CAN_GROW -> R.string.widget_streak_grow
        StreakState.LAST_DAY -> R.string.widget_streak_last
        StreakState.NONE -> R.string.widget_streak_none
    },
)

private fun base(): GlanceModifier =
    GlanceModifier.fillMaxSize().background(Bg).cornerRadius(22.dp).padding(12.dp)
        .clickable(actionStartActivity<MainActivity>())

/** Small widget (2×2): the flame, the day count and this week's days. */
class FitnexStreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadStreakData(context)
        provideContent { Content(context, data) }
    }

    @Composable
    private fun Content(context: Context, d: StreakWidgetData) {
        Column(base(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            if (!d.signedIn) {
                Text(context.getString(R.string.widget_signed_out), style = TextStyle(color = White, fontSize = 13.sp))
                return@Column
            }
            StreakHeadline(context, d, numberSp = 30)
            Spacer(GlanceModifier.height(8.dp))
            WeekDots(context, d, dot = 18)
            Spacer(GlanceModifier.height(6.dp))
            Text(message(context, d), style = TextStyle(color = Grey, fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 2)
        }
    }
}

/** Wide widget (4×2): the streak plus this week's workouts, minutes and sets. */
class FitnexStatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadStreakData(context)
        provideContent { Content(context, data) }
    }

    @Composable
    private fun Content(context: Context, d: StreakWidgetData) {
        Column(base(), verticalAlignment = Alignment.CenterVertically) {
            if (!d.signedIn) {
                Text(context.getString(R.string.widget_signed_out), style = TextStyle(color = White, fontSize = 13.sp))
                return@Column
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StreakHeadline(context, d, numberSp = 26)
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End) {
                    Text("FITNEX", style = TextStyle(color = ColorProvider(Color(0xFF8B5CF6)), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    if (d.best > 0) Text(context.getString(R.string.widget_streak_best, d.best), style = TextStyle(color = Grey, fontSize = 11.sp))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            WeekDots(context, d, dot = 18)
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Stat(d.weekWorkouts.toString(), context.getString(R.string.widget_stat_workouts), GlanceModifier.defaultWeight())
                Stat(d.weekMinutes.toString(), context.getString(R.string.widget_stat_minutes), GlanceModifier.defaultWeight())
                Stat(d.weekSets.toString(), context.getString(R.string.widget_stat_sets), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun Stat(value: String, label: String, modifier: GlanceModifier) {
        Box(modifier.padding(horizontal = 3.dp)) {
            Column(
                GlanceModifier.fillMaxWidth().background(Color(0xFF1E1E23)).cornerRadius(12.dp).padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text(label, style = TextStyle(color = Grey, fontSize = 10.sp), maxLines = 1)
            }
        }
    }
}

class FitnexStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FitnexStreakWidget()
}

class FitnexStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FitnexStatsWidget()
}

/** Refreshes every FITNEX widget (after a workout, at sign-in, on the daily reminder). */
internal suspend fun refreshStreakWidgets(context: Context) {
    FitnexStreakWidget().updateAll(context)
    FitnexStatsWidget().updateAll(context)
}
