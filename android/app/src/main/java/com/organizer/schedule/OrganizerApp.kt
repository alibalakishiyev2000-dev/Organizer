package com.organizer.schedule

import android.app.Application
import com.organizer.schedule.domain.repository.ScheduleRepository
import com.organizer.schedule.notification.LessonAlarmScheduler
import com.organizer.schedule.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Точка входа Hilt: создаёт канал уведомлений (API 26+) и планирует напоминания за 15 минут до уроков.
 */
@HiltAndroidApp
class OrganizerApp : Application() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var alarmScheduler: LessonAlarmScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        appScope.launch {
            repository.ensureSeeded()
            val lessons = repository.observeAllLessons().first()
            val prefs = repository.observePreferences().first()
            alarmScheduler.rescheduleUpcoming(
                lessons = lessons,
                enabled = prefs.notificationsEnabled,
                minutesBefore = prefs.notificationMinutesBefore
            )
        }
    }
}
