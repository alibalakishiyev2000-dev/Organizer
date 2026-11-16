package com.organizer.schedule.domain.usecase

import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * Фильтрует список уроков для конкретной даты с учётом чётности/нечётности недели и разовых занятий.
 */
class GetLessonsForDateUseCase @Inject constructor() {

    operator fun invoke(
        lessons: List<Lesson>,
        targetDate: LocalDate,
        evenWeekOverride: Boolean? = null
    ): List<Lesson> {
        val isEvenWeek = evenWeekOverride ?: isEvenWeek(targetDate)
        return lessons.filter { lesson ->
            when (lesson.recurrence) {
                RecurrenceType.WEEKLY -> lesson.dayOfWeek == targetDate.dayOfWeek
                RecurrenceType.EVEN_WEEK -> lesson.dayOfWeek == targetDate.dayOfWeek && isEvenWeek
                RecurrenceType.ODD_WEEK -> lesson.dayOfWeek == targetDate.dayOfWeek && !isEvenWeek
                RecurrenceType.ONCE -> lesson.specificDate == targetDate
            }
        }.sortedBy { it.startTime }
    }

    fun isEvenWeek(date: LocalDate): Boolean {
        val weekNumber = date.get(WeekFields.of(Locale("ru")).weekOfWeekBasedYear())
        return weekNumber % 2 == 0
    }
}
