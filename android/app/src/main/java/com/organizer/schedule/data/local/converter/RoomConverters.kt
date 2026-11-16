package com.organizer.schedule.data.local.converter

import androidx.room.TypeConverter
import com.organizer.schedule.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Конвертеры типов Java Time и перечислений для базы данных Room.
 */
class RoomConverters {

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun toLocalTime(seconds: Int?): LocalTime? = seconds?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromDayOfWeek(value: DayOfWeek?): Int? = value?.value

    @TypeConverter
    fun toDayOfWeek(day: Int?): DayOfWeek? = day?.let { DayOfWeek.of(it) }

    @TypeConverter
    fun fromRecurrence(value: RecurrenceType?): String? = value?.name

    @TypeConverter
    fun toRecurrence(name: String?): RecurrenceType? = name?.let { RecurrenceType.valueOf(it) }
}
