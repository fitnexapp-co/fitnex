package com.fitnex.core.designsystem.component.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * What the calendar shows for a day. A day with NO marker means "no activity recorded" —
 * it is deliberately different from [PLANNED_REST] (spec §17).
 */
enum class DayMarker { COMPLETED, SCHEDULED, PLANNED_REST, MISSED }

object CalendarMath {
    /**
     * Cells of a month grid, row by row, starting on [firstDayOfWeek]. Leading/trailing cells
     * outside the month are null. Always a whole number of weeks (35 or 42 cells, or 28).
     */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate?> {
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = ArrayList<LocalDate?>(42)
        repeat(leading) { cells += null }
        for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** Weekday initials in display order, e.g. L M X J V S D for Spanish. */
    fun weekdayLabels(locale: Locale, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<String> =
        (0 until 7).map { i ->
            val day = firstDayOfWeek.plus(i.toLong())
            // Spanish convention: Wednesday is "X" to avoid clashing with Tuesday "M".
            if (locale.language == "es" && day == DayOfWeek.WEDNESDAY) "X"
            else day.getDisplayName(TextStyle.NARROW_STANDALONE, locale).uppercase(locale)
        }

    /** "Noviembre 2026" — capitalised month name in the given locale. */
    fun monthTitle(month: YearMonth, locale: Locale): String {
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return name.replaceFirstChar { it.titlecase(locale) } + " " + month.year
    }
}
