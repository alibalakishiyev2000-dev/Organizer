package com.organizer.schedule.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Тип повторения урока в расписании.
 */
enum class RecurrenceType(val title: String) {
    WEEKLY("Каждую неделю"),
    EVEN_WEEK("Верхняя неделя"),
    ODD_WEEK("Нижняя неделя"),
    ONCE("Один раз")
}

/**
 * Режим определения текущей недели (Автоматически по календарю или вручную).
 */
enum class WeekParityMode(val title: String) {
    AUTO("Авто"),
    EVEN("Верхняя"),
    ODD("Нижняя")
}

/**
 * Тема оформления приложения.
 */
enum class AppThemeMode(val title: String) {
    SYSTEM("Система"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

/**
 * Курируемая палитра пастельных цветов для предметов (Material You стиль).
 */
enum class SubjectColor(
    val id: String,
    val title: String,
    val lightHex: Long,
    val darkHex: Long,
    val accentHex: Long
) {
    SAGE("sage", "Шалфейный", 0xFFDDECE0, 0xFF283D2E, 0xFF538262),
    SKY("sky", "Небесный", 0xFFD9EBFA, 0xFF21374A, 0xFF4778A8),
    LAVENDER("lavender", "Лавандовый", 0xFFE8E0F8, 0xFF33294A, 0xFF7257A6),
    PEACH("peach", "Персиковый", 0xFFFCE2D4, 0xFF4A2E20, 0xFFB86B46),
    LEMON("lemon", "Лимонный", 0xFFF7EFC6, 0xFF423B1B, 0xFF9A8327),
    ROSE("rose", "Розовый кварц", 0xFFF9D9E4, 0xFF482332, 0xFFB25377),
    MINT("mint", "Мятный", 0xFFD4F1EC, 0xFF1E3E39, 0xFF3C8A7E),
    SAND("sand", "Песочный", 0xFFEDE2D5, 0xFF3B3126, 0xFF8C7254);

    companion object {
        fun fromId(id: String): SubjectColor = entries.find { it.id == id } ?: SKY
    }
}

/**
 * Доменная модель урока / пары.
 */
data class Lesson(
    val id: Long = 0L,
    val subject: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val color: SubjectColor,
    val recurrence: RecurrenceType,
    val specificDate: LocalDate? = null,
    val notes: String = ""
)

/**
 * Доменная модель домашнего задания, привязанного к уроку.
 */
data class HomeworkTask(
    val id: Long = 0L,
    val lessonId: Long,
    val subjectName: String = "",
    val subjectColor: SubjectColor = SubjectColor.SKY,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate,
    val isCompleted: Boolean = false
)

/**
 * Настройки пользователя, сохраняемые в DataStore.
 */
data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val useSystemFont: Boolean = true,
    val highRefreshRate: Boolean = true,
    val notificationMinutesBefore: Int = 15,
    val notificationsEnabled: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val weekParityMode: WeekParityMode = WeekParityMode.AUTO
)

/**
 * Результат импорта расписания из файла (.json или .ics).
 */
data class ScheduleImportResult(
    val lessonsImported: Int,
    val homeworkImported: Int
)

/**
 * Состояние текущего и следующего урока в реальном времени.
 */
sealed interface CurrentLessonStatus {
    data class Active(
        val currentLesson: Lesson,
        val progress: Float,
        val minutesRemaining: Long,
        val nextLesson: Lesson?,
        val minutesUntilNext: Long?
    ) : CurrentLessonStatus

    data class Upcoming(
        val nextLesson: Lesson,
        val minutesUntilNext: Long
    ) : CurrentLessonStatus

    data object FinishedToday : CurrentLessonStatus
    data object NoLessonsToday : CurrentLessonStatus
}
