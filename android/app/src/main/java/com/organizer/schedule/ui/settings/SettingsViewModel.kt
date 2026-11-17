package com.organizer.schedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.ScheduleImportResult
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.domain.model.WeekParityMode
import com.organizer.schedule.domain.repository.ScheduleRepository
import com.organizer.schedule.domain.usecase.ExportScheduleToIcsUseCase
import com.organizer.schedule.domain.usecase.ParsedScheduleBackup
import com.organizer.schedule.domain.usecase.ScheduleBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * ViewModel экрана настроек (тема, системный шрифт, уведомления, экспорт/импорт расписания).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val exportScheduleToIcsUseCase: ExportScheduleToIcsUseCase,
    private val scheduleBackupUseCase: ScheduleBackupUseCase
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = repository.observePreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UserPreferences()
        )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.updateDynamicColor(enabled) }
    }

    fun setUseSystemFont(enabled: Boolean) {
        viewModelScope.launch { repository.updateUseSystemFont(enabled) }
    }

    fun setHighRefreshRate(enabled: Boolean) {
        viewModelScope.launch { repository.updateHighRefreshRate(enabled) }
    }

    fun setNotificationSettings(enabled: Boolean, minutesBefore: Int) {
        viewModelScope.launch { repository.updateNotificationSettings(enabled, minutesBefore) }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        viewModelScope.launch { repository.updateFirstDayOfWeek(day) }
    }

    fun setWeekParityMode(mode: WeekParityMode) {
        viewModelScope.launch { repository.updateWeekParityMode(mode) }
    }

    suspend fun buildScheduleIcsContent(): String {
        val lessons = repository.observeAllLessons().first()
        val prefs = repository.observePreferences().first()
        return withContext(Dispatchers.Default) {
            exportScheduleToIcsUseCase(
                lessons = lessons,
                weekParityMode = prefs.weekParityMode
            )
        }
    }

    suspend fun buildScheduleJsonBackup(): String {
        val lessons = repository.observeAllLessons().first()
        val homework = repository.observeAllHomework().first()
        return withContext(Dispatchers.Default) {
            scheduleBackupUseCase.exportToJson(lessons, homework)
        }
    }

    suspend fun buildBaseScheduleJsonBackup(): String = withContext(Dispatchers.Default) {
        val (baseLessons, baseHomework) = repository.getBaseScheduleSnapshot()
        scheduleBackupUseCase.exportToJson(baseLessons, baseHomework)
    }

    suspend fun parseScheduleBackup(rawContent: String): ParsedScheduleBackup {
        val prefs = repository.observePreferences().first()
        return withContext(Dispatchers.Default) {
            scheduleBackupUseCase.parseBackupFile(rawContent, prefs.weekParityMode)
        }
    }

    suspend fun importParsedSchedule(
        parsed: ParsedScheduleBackup,
        replaceExisting: Boolean
    ): ScheduleImportResult = repository.importSchedule(
        lessons = parsed.lessons,
        homework = parsed.homework,
        replaceExisting = replaceExisting
    )
}
