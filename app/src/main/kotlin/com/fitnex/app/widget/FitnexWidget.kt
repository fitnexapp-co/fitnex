package com.fitnex.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fitnex.app.MainActivity
import com.fitnex.app.R
import com.fitnex.core.data.profile.UserProfileRepository
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.stats.GlobalStatisticsRepository
import com.fitnex.core.data.today.TodayPlanRepository
import com.fitnex.core.datastore.UserPreferencesDataSource
import com.fitnex.core.model.AchievementsCalculator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun session(): SessionRepository
    fun today(): TodayPlanRepository
    fun stats(): GlobalStatisticsRepository
    fun preferences(): UserPreferencesDataSource
    fun profiles(): UserProfileRepository
}

/** What the widget shows; computed from real data only. */
private data class WidgetData(
    val signedIn: Boolean,
    val weekDone: Int = 0,
    val weekTarget: Int = 0,
    val streak: Int = 0,
    val line: String = "",
    val accent: Color = Color(0xFF8B5CF6),
)

/** Home-screen widget: this week's goal, the streak and today's (or the next) workout. */
class FitnexWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val uid = ep.session().currentUserId.first() ?: return WidgetData(signedIn = false)
        val prefs = ep.preferences().preferences.first()
        val profile = ep.profiles().get(uid)
        val sessions = ep.stats().observeCompletedSessions(uid).first()
        val target = prefs.weeklyGoal ?: profile?.availableDays?.size?.takeIf { it > 0 } ?: AchievementsCalculator.DEFAULT_WEEKLY_TARGET
        val a = AchievementsCalculator.compute(sessions, target, LocalDate.now(), ZoneId.systemDefault())
        val plan = ep.today().get(uid)
        val es = Locale.forLanguageTag("es")
        val nextDate = plan.nextDate
        val nextName = plan.nextRoutineName
        val line = when {
            plan.doneToday -> context.getString(R.string.widget_done_today)
            plan.restDay -> context.getString(R.string.widget_rest)
            plan.routineName != null -> context.getString(R.string.widget_today, plan.routineName)
            nextDate != null && nextName != null ->
                context.getString(R.string.widget_next, nextDate.dayOfWeek.getDisplayName(DateStyle.FULL, es), nextName)
            else -> context.getString(R.string.widget_no_plan)
        }
        return WidgetData(true, a.weekDone, a.weekTarget, a.streakWeeks, line, prefs.accentArgb?.let { Color(it) } ?: Color(0xFF8B5CF6))
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val white = ColorProvider(Color(0xFFF5F5F7))
        val grey = ColorProvider(Color(0xFF9A9AA3))
        Column(
            GlanceModifier.fillMaxSize().background(Color(0xFF141417)).cornerRadius(22.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("FITNEX", style = TextStyle(color = ColorProvider(d.accent), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            if (!d.signedIn) {
                Text(context.getString(R.string.widget_signed_out), style = TextStyle(color = white, fontSize = 14.sp))
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${d.weekDone}/${d.weekTarget}", style = TextStyle(color = white, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(6.dp))
                Text(context.getString(R.string.widget_week), style = TextStyle(color = grey, fontSize = 12.sp))
            }
            if (d.streak > 0) {
                Text(context.resources.getQuantityString(R.plurals.widget_streak, d.streak, d.streak), style = TextStyle(color = ColorProvider(d.accent), fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(d.line, style = TextStyle(color = white, fontSize = 13.sp), maxLines = 2)
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            FitnexWidget().updateAll(context)
            refreshStreakWidgets(context)
        }
    }
}

class FitnexWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FitnexWidget()
}
