package com.organizer.schedule.domain.usecase

import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.domain.model.SubjectColor
import com.organizer.schedule.domain.model.WeekParityMode
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    private val flexibleTimeFormatters = listOf(
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm:ss"),
        DateTimeFormatter.ofPattern("HH:mm:ss")
    )

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

    fun parseBackupFile(
        rawContent: String,
        weekParityMode: WeekParityMode = WeekParityMode.AUTO
    ): ParsedScheduleBackup {
        val cleaned = rawContent.replace("\uFEFF", "").trim()
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Файл расписания пуст")
        }
        return if (cleaned.startsWith("BEGIN:VCALENDAR", ignoreCase = true)) {
            parseIcs(cleaned, weekParityMode)
        } else {
            parseJson(cleaned)
        }
    }

    private fun parseJson(rawJson: String): ParsedScheduleBackup {
        val parsedRoot = try {
            JSONTokener(rawJson).nextValue()
        } catch (e: Exception) {
            throw IllegalArgumentException("Некорректный JSON-файл расписания", e)
        }

        val rootObj: JSONObject? = parsedRoot as? JSONObject
        val lessonsArray: JSONArray = when {
            rootObj != null -> {
                rootObj.optJSONArray("lessons")
                    ?: rootObj.optJSONArray("schedule")
                    ?: rootObj.optJSONArray("items")
                    ?: throw IllegalArgumentException("В JSON-файле не найден список уроков (lessons)")
            }
            parsedRoot is JSONArray -> parsedRoot
            else -> throw IllegalArgumentException("Неподдерживаемая структура JSON-файла")
        }

        val palette = SubjectColor.entries
        val lessons = buildList {
            for (i in 0 until lessonsArray.length()) {
                val obj = lessonsArray.optJSONObject(i) ?: continue
                val subject = obj.optSafeString("subject").trim()
                if (subject.isBlank()) continue

                val dayOfWeek = parseDayOfWeekSafe(obj)
                val startTime = parseTimeSafe(obj.optSafeString("startTime"), LocalTime.of(9, 0))
                val parsedEnd = parseTimeSafe(obj.optSafeString("endTime"), safePlusMinutes(startTime, 90))
                val endTime = if (parsedEnd.isAfter(startTime)) {
                    parsedEnd
                } else {
                    safePlusMinutes(startTime, 45)
                }

                val recurrenceRaw = obj.optSafeString("recurrence", RecurrenceType.WEEKLY.name)
                    .trim()
                    .uppercase(Locale.ROOT)
                val recurrence = runCatching { RecurrenceType.valueOf(recurrenceRaw) }
                    .getOrDefault(RecurrenceType.WEEKLY)

                val specificDate = obj.optSafeString("specificDate")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { rawDate ->
                        runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull()
                    }
                    ?: if (recurrence == RecurrenceType.ONCE) LocalDate.now() else null

                val colorRaw = obj.optSafeString("colorId").ifBlank { obj.optSafeString("color") }
                val color = if (colorRaw.isNotBlank()) {
                    SubjectColor.fromId(colorRaw.trim().lowercase(Locale.ROOT))
                } else {
                    palette[i % palette.size]
                }

                val rawId = obj.optSafeLong("id", (i + 1).toLong())

                add(
                    Lesson(
                        id = rawId,
                        subject = subject,
                        teacher = obj.optSafeString("teacher").trim(),
                        room = obj.optSafeString("room").trim(),
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime,
                        color = color,
                        recurrence = recurrence,
                        specificDate = specificDate,
                        notes = obj.optSafeString("notes").trim()
                    )
                )
            }
        }

        if (lessons.isEmpty()) {
            throw IllegalArgumentException("В файле не найдено ни одного урока")
        }

        val hwArray = rootObj?.optJSONArray("homework") ?: JSONArray()
        val homework = buildList {
            for (i in 0 until hwArray.length()) {
                val obj = hwArray.optJSONObject(i) ?: continue
                val title = obj.optSafeString("title").trim()
                if (title.isBlank()) continue
                val lessonId = obj.optSafeLong("lessonId", -1L)
                if (lessonId < 0L) continue

                val dueDateRaw = obj.optSafeString("dueDate").trim()
                val dueDate = if (dueDateRaw.isNotBlank()) {
                    runCatching { LocalDate.parse(dueDateRaw.take(10)) }
                        .getOrDefault(LocalDate.now().plusDays(1))
                } else {
                    LocalDate.now().plusDays(1)
                }

                add(
                    HomeworkTask(
                        id = 0L,
                        lessonId = lessonId,
                        title = title,
                        description = obj.optSafeString("description").trim(),
                        dueDate = dueDate,
                        isCompleted = obj.optBoolean("isCompleted", false)
                    )
                )
            }
        }

        return ParsedScheduleBackup(lessons = lessons, homework = homework)
    }

    private fun parseIcs(
        rawIcs: String,
        weekParityMode: WeekParityMode = WeekParityMode.AUTO
    ): ParsedScheduleBackup {
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
                        val startDt = runCatching { parseIcsDateTime(dtStartRaw) }.getOrNull()
                        if (startDt != null) {
                            val endDt = if (dtEndRaw.isNotBlank()) {
                                runCatching { parseIcsDateTime(dtEndRaw) }.getOrDefault(startDt.plusMinutes(90))
                            } else {
                                startDt.plusMinutes(90)
                            }
                            val recurrence = when {
                                rrule.contains("INTERVAL=2", ignoreCase = true) -> {
                                    if (getLessonsForDateUseCase.isEvenWeek(startDt.toLocalDate(), weekParityMode)) {
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

                            val startTime = startDt.toLocalTime()
                            val candidateEnd = endDt.toLocalTime()
                            val endTime = if (candidateEnd.isAfter(startTime)) {
                                candidateEnd
                            } else {
                                safePlusMinutes(startTime, 45)
                            }

                            lessons.add(
                                Lesson(
                                    id = (++index).toLong(),
                                    subject = unescapeIcsText(summary),
                                    teacher = teacher,
                                    room = cleanRoom,
                                    dayOfWeek = startDt.dayOfWeek,
                                    startTime = startTime,
                                    endTime = endTime,
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
                    }
                    inEvent = false
                }
                inEvent -> {
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).uppercase(Locale.ROOT)
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

        if (lessons.isEmpty()) {
            throw IllegalArgumentException("В файле .ics не найдено событий расписания")
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

    private fun JSONObject.optSafeString(key: String, fallback: String = ""): String {
        if (!has(key) || isNull(key)) return fallback
        val value = opt(key) ?: return fallback
        val str = value.toString()
        return if (str.equals("null", ignoreCase = true)) fallback else str
    }

    private fun JSONObject.optSafeLong(key: String, fallback: Long): Long {
        if (!has(key) || isNull(key)) return fallback
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    private fun parseDayOfWeekSafe(obj: JSONObject): DayOfWeek {
        if (!obj.has("dayOfWeek") || obj.isNull("dayOfWeek")) return DayOfWeek.MONDAY
        return when (val raw = obj.opt("dayOfWeek")) {
            is Number -> DayOfWeek.of(raw.toInt().coerceIn(1, 7))
            is String -> {
                val text = raw.trim().uppercase(Locale.ROOT)
                text.toIntOrNull()?.let { return DayOfWeek.of(it.coerceIn(1, 7)) }
                when (text) {
                    "MONDAY", "MON", "MO", "ПН", "ПОНЕДЕЛЬНИК" -> DayOfWeek.MONDAY
                    "TUESDAY", "TUE", "TU", "ВТ", "ВТОРНИК" -> DayOfWeek.TUESDAY
                    "WEDNESDAY", "WED", "WE", "СР", "СРЕДА" -> DayOfWeek.WEDNESDAY
                    "THURSDAY", "THU", "TH", "ЧТ", "ЧЕТВЕРГ" -> DayOfWeek.THURSDAY
                    "FRIDAY", "FRI", "FR", "ПТ", "ПЯТНИЦА" -> DayOfWeek.FRIDAY
                    "SATURDAY", "SAT", "SA", "СБ", "СУББОТА" -> DayOfWeek.SATURDAY
                    "SUNDAY", "SUN", "SU", "ВС", "ВОСКРЕСЕНЬЕ" -> DayOfWeek.SUNDAY
                    else -> runCatching { DayOfWeek.valueOf(text) }.getOrDefault(DayOfWeek.MONDAY)
                }
            }
            else -> DayOfWeek.MONDAY
        }
    }

    private fun parseTimeSafe(raw: String, fallback: LocalTime): LocalTime {
        val clean = raw.trim()
        if (clean.isBlank()) return fallback
        for (fmt in flexibleTimeFormatters) {
            val parsed = runCatching { LocalTime.parse(clean, fmt) }.getOrNull()
            if (parsed != null) return parsed
        }
        return runCatching { LocalTime.parse(clean) }.getOrDefault(fallback)
    }

    private fun safePlusMinutes(time: LocalTime, minutesToAdd: Int): LocalTime {
        val startMinutes = time.hour * 60 + time.minute
        val targetMinutes = (startMinutes + minutesToAdd).coerceAtMost(23 * 60 + 59)
        return LocalTime.of(targetMinutes / 60, targetMinutes % 60)
    }
}

