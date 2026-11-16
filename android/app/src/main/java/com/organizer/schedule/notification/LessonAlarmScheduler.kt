package com.organizer.schedule.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.usecase.GetLessonsForDateUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планирует точные будильники AlarmManager за N минут до ближайших уроков.
 */
@Singleton
class LessonAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getLessonsForDateUseCase: GetLessonsForDateUseCase
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun rescheduleUpcoming(
        lessons: List<Lesson>,
        enabled: Boolean,
        minutesBefore: Int
    ) {
        val manager = alarmManager ?: return
        // Отменяем предыдущие запланированные будильники
        lessons.forEach { cancelLessonAlarm(manager, it.id) }
        if (!enabled) return

        val now = LocalDateTime.now()
        // Планируем уведомления на ближайшие 7 дней
        for (offset in 0L..6L) {
            val date = LocalDate.now().plusDays(offset)
            val dayLessons = getLessonsForDateUseCase(lessons, date)

            dayLessons.forEach { lesson ->
                val lessonDateTime = LocalDateTime.of(date, lesson.startTime)
                val triggerDateTime = lessonDateTime.minusMinutes(minutesBefore.toLong())

                if (triggerDateTime.isAfter(now)) {
                    scheduleSingleAlarm(manager, lesson, triggerDateTime, minutesBefore)
                }
            }
        }
    }

    private fun scheduleSingleAlarm(
        manager: AlarmManager,
        lesson: Lesson,
        triggerTime: LocalDateTime,
        minutesBefore: Int
    ) {
        runCatching {
            val triggerMillis = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = Intent(context, LessonAlarmReceiver::class.java).apply {
                putExtra(EXTRA_LESSON_ID, lesson.id)
                putExtra(EXTRA_SUBJECT, lesson.subject)
                putExtra(EXTRA_ROOM, lesson.room)
                putExtra(EXTRA_START_TIME, lesson.startTime.format(timeFormatter))
                putExtra(EXTRA_MINUTES_BEFORE, minutesBefore)
            }
            val requestCode = buildRequestCode(lesson.id, triggerTime.dayOfWeek.value)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
            } else {
                true
            }

            if (canUseExact) {
                try {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        }
    }

    private fun cancelLessonAlarm(manager: AlarmManager, lessonId: Long) {
        for (day in 1..7) {
            runCatching {
                val requestCode = buildRequestCode(lessonId, day)
                val intent = Intent(context, LessonAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    manager.cancel(pendingIntent)
                }
            }
        }
    }

    private fun buildRequestCode(lessonId: Long, dayOfWeek: Int): Int {
        val folded = (lessonId xor (lessonId ushr 32)).toInt()
        return folded * 31 + dayOfWeek
    }

    companion object {
        const val EXTRA_LESSON_ID = "extra_lesson_id"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_ROOM = "extra_room"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_MINUTES_BEFORE = "extra_minutes_before"
    }
}
