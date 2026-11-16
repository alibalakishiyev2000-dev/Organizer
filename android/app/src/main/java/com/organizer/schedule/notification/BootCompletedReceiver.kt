package com.organizer.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.organizer.schedule.domain.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Восстанавливает запланированные уведомления после перезагрузки телефона.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var alarmScheduler: LessonAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val lessons = repository.observeAllLessons().first()
                    val prefs = repository.observePreferences().first()
                    alarmScheduler.rescheduleUpcoming(
                        lessons = lessons,
                        enabled = prefs.notificationsEnabled,
                        minutesBefore = prefs.notificationMinutesBefore
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
