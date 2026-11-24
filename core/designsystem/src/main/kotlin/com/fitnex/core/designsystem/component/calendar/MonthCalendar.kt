package com.fitnex.core.designsystem.component.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitnex.core.designsystem.R
import com.fitnex.core.designsystem.locale.rememberAppLocale
import com.fitnex.core.designsystem.theme.FitnexColors
import com.fitnex.core.designsystem.theme.FitnexTheme
import com.fitnex.core.designsystem.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Month grid with activity markers. Stateless: the caller owns [month] and [selected].
 * Swiping is intentionally not used for month changes so it never fights vertical scrolling.
 */
@Composable
fun FitnexMonthCalendar(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    marker: (LocalDate) -> DayMarker?,
    modifier: Modifier = Modifier,
    locale: Locale = rememberAppLocale(),
) {
    val colors = FitnexTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.ds_previous_month), tint = colors.textPrimary)
            }
            Text(
                CalendarMath.monthTitle(month, locale),
                style = FitnexTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).testTag("calendar_month_title"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.ds_next_month), tint = colors.textPrimary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
            CalendarMath.weekdayLabels(locale).forEach {
                Text(
                    it,
                    style = FitnexTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val reduceMotion = com.fitnex.core.designsystem.theme.FitnexTheme.appearance.reduceMotion
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                // "Reducir animaciones": the month changes with a short fade, without sliding.
                if (reduceMotion) fadeIn(androidx.compose.animation.core.tween(120)) togetherWith fadeOut(androidx.compose.animation.core.tween(90))
                else (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "month",
        ) { shownMonth ->
            val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CalendarMath.monthGrid(shownMonth).chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val dayMarker = marker(date)
                                    DayCell(
                                        date = date,
                                        isSelected = date == selected,
                                        isToday = date == today,
                                        marker = dayMarker,
                                        colors = colors,
                                        description = date.format(dateFormat) + markerSuffix(dayMarker),
                                        onClick = { onSelect(date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun markerSuffix(marker: DayMarker?): String = when (marker) {
    DayMarker.COMPLETED -> ", " + stringResource(R.string.ds_marker_completed)
    DayMarker.SCHEDULED -> ", " + stringResource(R.string.ds_marker_scheduled)
    DayMarker.PLANNED_REST -> ", " + stringResource(R.string.ds_marker_rest)
    DayMarker.MISSED -> ", " + stringResource(R.string.ds_marker_missed)
    null -> ""
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    marker: DayMarker?,
    colors: FitnexColors,
    description: String,
    onClick: () -> Unit,
) {
    val background = when {
        isSelected -> colors.accent
        marker == DayMarker.COMPLETED -> colors.accent.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.accent, CircleShape) else Modifier)
            .selectable(selected = isSelected, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description }
            .testTag("day_${date}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = FitnexTheme.typography.bodyMedium,
            color = if (isSelected) colors.onAccent else colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        MarkerDot(marker, isSelected, colors)
    }
}

@Composable
private fun MarkerDot(marker: DayMarker?, onAccent: Boolean, colors: FitnexColors) {
    val color = when (marker) {
        DayMarker.COMPLETED -> if (onAccent) colors.onAccent else colors.accent
        DayMarker.SCHEDULED -> colors.textPrimary
        DayMarker.PLANNED_REST -> colors.success
        DayMarker.MISSED -> colors.textDisabled
        null -> Color.Transparent
    }
    Box(Modifier.size(5.dp).background(color, CircleShape))
}

/** Legend explaining every marker; "no marker" is spelled out as "no activity recorded". */
@Composable
fun CalendarLegend(modifier: Modifier = Modifier) {
    val colors = FitnexTheme.colors
    val items = listOf(
        colors.accent to stringResource(R.string.ds_marker_completed),
        colors.textPrimary to stringResource(R.string.ds_marker_scheduled),
        colors.success to stringResource(R.string.ds_marker_rest),
        colors.textDisabled to stringResource(R.string.ds_marker_missed),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (color, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Text(label, style = FitnexTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(start = Spacing.sm))
                    }
                }
            }
        }
        Text(stringResource(R.string.ds_marker_none_hint), style = FitnexTheme.typography.bodySmall, color = colors.textDisabled)
    }
}
