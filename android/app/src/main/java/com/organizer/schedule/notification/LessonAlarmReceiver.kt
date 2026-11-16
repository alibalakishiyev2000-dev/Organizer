package com.organizer.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Приёмник срабатывания будильника для показа уведомления перед уроком.
 */
class LessonAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lessonId = intent.getLongExtra(LessonAlarmScheduler.EXTRA_LESSON_ID, 0L)
        val subject = intent.getStringExtra(LessonAlarmScheduler.EXTRA_SUBJECT) ?: return
        val room = intent.getStringExtra(LessonAlarmScheduler.EXTRA_ROOM) ?: ""
        val startTime = intent.getStringExtra(LessonAlarmScheduler.EXTRA_START_TIME) ?: ""
        val minutesBefore = intent.getIntExtra(LessonAlarmScheduler.EXTRA_MINUTES_BEFORE, 15)

        NotificationHelper.showLessonReminder(
            context = context,
            lessonId = lessonId,
            subject = subject,
            room = room,
            startTimeText = startTime,
            minutesBefore = minutesBefore
        )
    }
}
