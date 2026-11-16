package com.organizer.schedule.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.organizer.schedule.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Таблица уроков и пар в Room.
 */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val subject: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val colorId: String,
    val recurrence: RecurrenceType,
    val specificDate: LocalDate? = null,
    val notes: String = ""
)
