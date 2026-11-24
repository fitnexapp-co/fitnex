package com.fitnex.core.designsystem

import com.fitnex.core.designsystem.component.calendar.CalendarMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMathTest {
    private val es = Locale.forLanguageTag("es-ES")

    @Test
    fun november2026StartsOnSundayInAMondayFirstGrid() {
        // 1 Nov 2026 is a Sunday → 6 empty leading cells.
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 11))
        assertEquals(6, grid.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), grid[6])
        assertEquals(LocalDate.of(2026, 11, 30), grid.last { it != null })
        assertEquals(0, grid.size % 7)
        assertEquals(42, grid.size)
    }

    @Test
    fun february2027FitsExactlyFourWeeks() {
        // 1 Feb 2027 is a Monday and February 2027 has 28 days.
        val grid = CalendarMath.monthGrid(YearMonth.of(2027, 2))
        assertEquals(28, grid.size)
        assertTrue(grid.all { it != null })
    }

    @Test
    fun leapDayIsIncluded() {
        val grid = CalendarMath.monthGrid(YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 29), grid.last { it != null })
    }

    @Test
    fun sundayFirstGridShiftsLeadingCells() {
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), grid[0])
        assertNull(grid.last())
    }

    @Test
    fun spanishWeekdaysUseXForWednesday() {
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), CalendarMath.weekdayLabels(es))
    }

    @Test
    fun spanishMonthTitleIsCapitalised() {
        assertEquals("Noviembre 2026", CalendarMath.monthTitle(YearMonth.of(2026, 11), es))
    }
}
