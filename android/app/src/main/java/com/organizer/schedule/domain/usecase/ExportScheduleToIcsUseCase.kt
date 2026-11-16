package com.organizer.schedule.domain.usecase

import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Генерирует содержимое файла формата .ics (RFC 5545 / iCalendar)
 * для импорта расписания уроков в Google Календарь и другие календари.
 */
class ExportScheduleToIcsUseCase @Inject constructor(
    private val getLessonsForDateUseCase: GetLessonsForDateUseCase
) {
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    operator fun invoke(
        lessons: List<Lesson>,
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val nowStamp = LocalDateTime.now(ZoneOffset.UTC).format(utcFormatter)
        val sb = StringBuilder()

        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Organizer//Schedule App//RU")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:Органайзер — Расписание")
        sb.appendLine("X-WR-TIMEZONE:${zoneId.id}")

        lessons.forEach { lesson ->
            val firstDate = resolveFirstOccurrenceDate(lesson, referenceDate)
            val startZdt = LocalDateTime.of(firstDate, lesson.startTime)
                .atZone(zoneId)
                .withZoneSameInstant(ZoneOffset.UTC)
            val endZdt = LocalDateTime.of(firstDate, lesson.endTime)
                .atZone(zoneId)
                .withZoneSameInstant(ZoneOffset.UTC)

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:organizer-lesson-${lesson.id}@organizer.schedule")
            sb.appendLine("DTSTAMP:$nowStamp")
            sb.appendLine("DTSTART:${startZdt.format(utcFormatter)}")
            sb.appendLine("DTEND:${endZdt.format(utcFormatter)}")
            sb.appendLine("SUMMARY:${escapeIcsText(lesson.subject)}")

            if (lesson.room.isNotBlank()) {
                sb.appendLine("LOCATION:${escapeIcsText("Каб. ${lesson.room}")}")
            }

            val descParts = buildList {
                if (lesson.teacher.isNotBlank()) add("Преподаватель: ${lesson.teacher}")
                if (lesson.room.isNotBlank()) add("Кабинет: ${lesson.room}")
                add("Повторение: ${lesson.recurrence.title}")
                if (lesson.notes.isNotBlank()) add(lesson.notes)
            }
            if (descParts.isNotEmpty()) {
                sb.appendLine("DESCRIPTION:${escapeIcsText(descParts.joinToString("\n"))}")
            }

            val byDay = lesson.dayOfWeek.toIcsByDay()
            when (lesson.recurrence) {
                RecurrenceType.WEEKLY -> sb.appendLine("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=$byDay")
                RecurrenceType.EVEN_WEEK,
                RecurrenceType.ODD_WEEK -> sb.appendLine("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=$byDay")
                RecurrenceType.ONCE -> Unit
            }

            sb.appendLine("END:VEVENT")
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString().replace("\n", "\r\n")
    }

    private fun resolveFirstOccurrenceDate(lesson: Lesson, fromDate: LocalDate): LocalDate {
        if (lesson.recurrence == RecurrenceType.ONCE && lesson.specificDate != null) {
            return lesson.specificDate
        }

        val daysUntil = (lesson.dayOfWeek.value - fromDate.dayOfWeek.value + 7) % 7
        var candidate = fromDate.plusDays(daysUntil.toLong())

        when (lesson.recurrence) {
            RecurrenceType.EVEN_WEEK -> {
                if (!getLessonsForDateUseCase.isEvenWeek(candidate)) {
                    candidate = candidate.plusWeeks(1)
                }
            }
            RecurrenceType.ODD_WEEK -> {
                if (getLessonsForDateUseCase.isEvenWeek(candidate)) {
                    candidate = candidate.plusWeeks(1)
                }
            }
            else -> Unit
        }
        return candidate
    }

    private fun DayOfWeek.toIcsByDay(): String = when (this) {
        DayOfWeek.MONDAY -> "MO"
        DayOfWeek.TUESDAY -> "TU"
        DayOfWeek.WEDNESDAY -> "WE"
        DayOfWeek.THURSDAY -> "TH"
        DayOfWeek.FRIDAY -> "FR"
        DayOfWeek.SATURDAY -> "SA"
        DayOfWeek.SUNDAY -> "SU"
    }

    private fun escapeIcsText(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
}
