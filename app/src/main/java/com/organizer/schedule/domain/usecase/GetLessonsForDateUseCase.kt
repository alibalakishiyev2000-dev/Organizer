package com.organizer.schedule.domain.usecase

import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.domain.model.WeekParityMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * Фильтрует список уроков для конкретной даты с учётом чётности/нечётности недели и разовых занятий.
 */
class GetLessonsForDateUseCase @Inject constructor() {

    operator fun invoke(
        lessons: List<Lesson>,
        targetDate: LocalDate,
        evenWeekOverride: Boolean? = null,
        weekParityMode: WeekParityMode = WeekParityMode.AUTO,
        referenceDate: LocalDate = LocalDate.now()
    ): List<Lesson> {
        val isEvenWeek = evenWeekOverride ?: isEvenWeek(
            date = targetDate,
            mode = weekParityMode,
            referenceDate = referenceDate
        )
        return lessons.filter { lesson ->
            when (lesson.recurrence) {
                RecurrenceType.WEEKLY -> lesson.dayOfWeek == targetDate.dayOfWeek
                RecurrenceType.EVEN_WEEK -> lesson.dayOfWeek == targetDate.dayOfWeek && isEvenWeek
                RecurrenceType.ODD_WEEK -> lesson.dayOfWeek == targetDate.dayOfWeek && !isEvenWeek
                RecurrenceType.ONCE -> lesson.specificDate == targetDate
            }
        }.sortedBy { it.startTime }
    }

    fun calendarWeekNumber(date: LocalDate): Int =
        date.get(WeekFields.of(Locale("ru")).weekOfWeekBasedYear())

    fun isEvenWeek(
        date: LocalDate,
        mode: WeekParityMode = WeekParityMode.AUTO,
        referenceDate: LocalDate = LocalDate.now()
    ): Boolean = when (mode) {
        WeekParityMode.AUTO -> calendarWeekNumber(date) % 2 == 0
        WeekParityMode.EVEN, WeekParityMode.ODD -> {
            val baseEven = (mode == WeekParityMode.EVEN)
            val refMonday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val targetMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekDiff = ChronoUnit.WEEKS.between(refMonday, targetMonday)
            if (abs(weekDiff) % 2L == 0L) baseEven else !baseEven
        }
    }
}
