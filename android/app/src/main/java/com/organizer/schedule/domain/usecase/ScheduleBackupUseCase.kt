package com.organizer.schedule.domain.usecase

import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.domain.model.SubjectColor
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Результат разбора файла расписания (.json или .ics).
 */
data class ParsedScheduleBackup(
    val lessons: List<Lesson>,
    val homework: List<HomeworkTask>
)

/**
 * Экспорт и импорт расписания в формате резервной копии .json и календаря .ics.
 */
class ScheduleBackupUseCase @Inject constructor(
    private val getLessonsForDateUseCase: GetLessonsForDateUseCase
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun exportToJson(lessons: List<Lesson>, homework: List<HomeworkTask>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", LocalDateTime.now().toString())

        val lessonsArray = JSONArray()
        lessons.forEach { lesson ->
            lessonsArray.put(
                JSONObject().apply {
                    put("id", lesson.id)
                    put("subject", lesson.subject)
                    put("teacher", lesson.teacher)
                    put("room", lesson.room)
                    put("dayOfWeek", lesson.dayOfWeek.value)
                    put("startTime", lesson.startTime.format(timeFormatter))
                    put("endTime", lesson.endTime.format(timeFormatter))
                    put("colorId", lesson.color.id)
                    put("recurrence", lesson.recurrence.name)
                    if (lesson.specificDate != null) {
                        put("specificDate", lesson.specificDate.toString())
                    }
                    put("notes", lesson.notes)
                }
            )
        }
        root.put("lessons", lessonsArray)

        val homeworkArray = JSONArray()
        homework.forEach { task ->
            homeworkArray.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("lessonId", task.lessonId)
                    put("title", task.title)
                    put("description", task.description)
                    put("dueDate", task.dueDate.toString())
                    put("isCompleted", task.isCompleted)
                }
            )
        }
        root.put("homework", homeworkArray)

        return root.toString(2)
    }

    fun parseBackupFile(rawContent: String): ParsedScheduleBackup {
        val trimmed = rawContent.trimStart()
        return if (trimmed.startsWith("BEGIN:VCALENDAR", ignoreCase = true)) {
            parseIcs(rawContent)
        } else {
            parseJson(rawContent)
        }
    }

    private fun parseJson(rawJson: String): ParsedScheduleBackup {
        val root = JSONObject(rawJson)
        val lessonsArray = root.optJSONArray("lessons") ?: JSONArray()
        val lessons = buildList {
            for (i in 0 until lessonsArray.length()) {
                val obj = lessonsArray.getJSONObject(i)
                val subject = obj.optString("subject").trim()
                if (subject.isBlank()) continue
                val dayValue = obj.optInt("dayOfWeek", 1).coerceIn(1, 7)
                val startTime = runCatching { LocalTime.parse(obj.getString("startTime")) }
                    .getOrDefault(LocalTime.of(9, 0))
                val endTime = runCatching { LocalTime.parse(obj.getString("endTime")) }
                    .getOrDefault(startTime.plusMinutes(90))
                val recurrence = runCatching {
                    RecurrenceType.valueOf(obj.optString("recurrence", RecurrenceType.WEEKLY.name))
                }.getOrDefault(RecurrenceType.WEEKLY)
                val specificDate = obj.optString("specificDate").takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }

                add(
                    Lesson(
                        id = obj.optLong("id", (i + 1).toLong()),
                        subject = subject,
                        teacher = obj.optString("teacher", ""),
                        room = obj.optString("room", ""),
                        dayOfWeek = DayOfWeek.of(dayValue),
                        startTime = startTime,
                        endTime = endTime,
                        color = SubjectColor.fromId(obj.optString("colorId", "sky")),
                        recurrence = recurrence,
                        specificDate = specificDate,
                        notes = obj.optString("notes", "")
                    )
                )
            }
        }

        val hwArray = root.optJSONArray("homework") ?: JSONArray()
        val homework = buildList {
            for (i in 0 until hwArray.length()) {
                val obj = hwArray.getJSONObject(i)
                val title = obj.optString("title").trim()
                if (title.isBlank()) continue
                val dueDate = runCatching { LocalDate.parse(obj.optString("dueDate")) }
                    .getOrDefault(LocalDate.now().plusDays(1))
                add(
                    HomeworkTask(
                        id = 0L,
                        lessonId = obj.optLong("lessonId", 0L),
                        title = title,
                        description = obj.optString("description", ""),
                        dueDate = dueDate,
                        isCompleted = obj.optBoolean("isCompleted", false)
                    )
                )
            }
        }

        return ParsedScheduleBackup(lessons = lessons, homework = homework)
    }

    private fun parseIcs(rawIcs: String): ParsedScheduleBackup {
        // Разворачиваем перенесённые строки RFC 5545 (CRLF + пробел/таб)
        val unfolded = rawIcs
            .replace("\r\n ", "")
            .replace("\r\n\t", "")
            .replace("\n ", "")
            .replace("\n\t", "")

        val lines = unfolded.lines()
        val lessons = mutableListOf<Lesson>()
        val palette = SubjectColor.entries
        var inEvent = false
        var summary = ""
        var location = ""
        var description = ""
        var dtStartRaw = ""
        var dtEndRaw = ""
        var rrule = ""
        var index = 0

        for (line in lines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true
                    summary = ""
                    location = ""
                    description = ""
                    dtStartRaw = ""
                    dtEndRaw = ""
                    rrule = ""
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent && summary.isNotBlank() && dtStartRaw.isNotBlank()) {
                        val startDt = parseIcsDateTime(dtStartRaw)
                        val endDt = if (dtEndRaw.isNotBlank()) {
                            parseIcsDateTime(dtEndRaw)
                        } else {
                            startDt.plusMinutes(90)
                        }
                        val recurrence = when {
                            rrule.contains("INTERVAL=2", ignoreCase = true) -> {
                                if (getLessonsForDateUseCase.isEvenWeek(startDt.toLocalDate())) {
                                    RecurrenceType.EVEN_WEEK
                                } else {
                                    RecurrenceType.ODD_WEEK
                                }
                            }
                            rrule.contains("FREQ=WEEKLY", ignoreCase = true) -> RecurrenceType.WEEKLY
                            else -> RecurrenceType.ONCE
                        }

                        val cleanDesc = unescapeIcsText(description)
                        val teacher = cleanDesc.lines()
                            .firstOrNull { it.startsWith("Преподаватель:", ignoreCase = true) }
                            ?.substringAfter(":")
                            ?.trim()
                            .orEmpty()

                        val cleanRoom = unescapeIcsText(location)
                            .removePrefix("Каб. ")
                            .trim()

                        lessons.add(
                            Lesson(
                                id = (++index).toLong(),
                                subject = unescapeIcsText(summary),
                                teacher = teacher,
                                room = cleanRoom,
                                dayOfWeek = startDt.dayOfWeek,
                                startTime = startDt.toLocalTime(),
                                endTime = if (endDt.toLocalTime().isAfter(startDt.toLocalTime())) {
                                    endDt.toLocalTime()
                                } else {
                                    startDt.toLocalTime().plusMinutes(45)
                                },
                                color = palette[(index - 1) % palette.size],
                                recurrence = recurrence,
                                specificDate = if (recurrence == RecurrenceType.ONCE) startDt.toLocalDate() else null,
                                notes = cleanDesc.lines()
                                    .filterNot {
                                        it.startsWith("Преподаватель:", ignoreCase = true) ||
                                            it.startsWith("Кабинет:", ignoreCase = true) ||
                                            it.startsWith("Повторение:", ignoreCase = true)
                                    }
                                    .joinToString("\n")
                                    .trim()
                            )
                        )
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).uppercase()
                        val value = line.substring(colonIndex + 1)
                        when {
                            key == "SUMMARY" || key.startsWith("SUMMARY;") -> summary = value
                            key == "LOCATION" || key.startsWith("LOCATION;") -> location = value
                            key == "DESCRIPTION" || key.startsWith("DESCRIPTION;") -> description = value
                            key == "DTSTART" || key.startsWith("DTSTART;") -> dtStartRaw = value
                            key == "DTEND" || key.startsWith("DTEND;") -> dtEndRaw = value
                            key == "RRULE" -> rrule = value
                        }
                    }
                }
            }
        }

        return ParsedScheduleBackup(lessons = lessons, homework = emptyList())
    }

    private fun parseIcsDateTime(raw: String): LocalDateTime {
        val clean = raw.trim()
        return if (clean.endsWith("Z")) {
            val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            LocalDateTime.parse(clean, fmt)
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        } else if (clean.contains("T")) {
            val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            LocalDateTime.parse(clean.take(15), fmt)
        } else {
            val date = LocalDate.parse(clean.take(8), DateTimeFormatter.BASIC_ISO_DATE)
            LocalDateTime.of(date, LocalTime.of(9, 0))
        }
    }

    private fun unescapeIcsText(text: String): String = text
        .replace("\\n", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
}
