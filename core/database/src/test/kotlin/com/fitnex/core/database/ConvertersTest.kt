package com.fitnex.core.database

import com.fitnex.core.database.converter.Converters
import com.fitnex.core.model.Equipment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test
    fun roundTrips() {
        val now = Instant.parse("2026-11-19T10:15:30.123Z")
        assertEquals(now, c.longToInstant(c.instantToLong(now)))

        val day = LocalDate.of(2026, 2, 28)
        assertEquals(day, c.epochDayToDate(c.dateToEpochDay(day)))

        val days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)
        assertEquals("MONDAY,FRIDAY", c.daysToString(days))
        assertEquals(days, c.stringToDays(c.daysToString(days)))
        assertEquals(emptySet<DayOfWeek>(), c.stringToDays(""))

        val eq = setOf(Equipment.DUMBBELL, Equipment.BODYWEIGHT)
        assertEquals(eq, c.stringToEquipment(c.equipmentToString(eq)))

        val list = listOf("sin gluten", "vegetariano, \"estricto\"")
        assertEquals(list, c.jsonToStringList(c.stringListToJson(list)))
    }
}
