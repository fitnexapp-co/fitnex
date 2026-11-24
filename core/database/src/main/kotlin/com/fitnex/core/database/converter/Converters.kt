package com.fitnex.core.database.converter

import androidx.room.TypeConverter
import com.fitnex.core.model.Equipment
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Storage formats:
 *  - Instant   -> epoch millis (UTC)
 *  - LocalDate -> epoch day (allows fast range queries for the calendar)
 *  - Sets of enums -> comma separated names (stable, human readable in exported schemas)
 */
class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun daysToString(value: Set<DayOfWeek>): String = value.sorted().joinToString(",") { it.name }
    @TypeConverter fun stringToDays(value: String): Set<DayOfWeek> =
        if (value.isBlank()) emptySet() else value.split(',').mapTo(mutableSetOf()) { DayOfWeek.valueOf(it) }

    @TypeConverter fun equipmentToString(value: Set<Equipment>): String = value.sorted().joinToString(",") { it.name }
    @TypeConverter fun stringToEquipment(value: String): Set<Equipment> =
        if (value.isBlank()) emptySet() else value.split(',').mapTo(mutableSetOf()) { Equipment.valueOf(it) }

    @TypeConverter fun stringListToJson(value: List<String>): String = json.encodeToString(stringList, value)
    @TypeConverter fun jsonToStringList(value: String): List<String> = json.decodeFromString(stringList, value)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val stringList = ListSerializer(String.serializer())
    }
}
