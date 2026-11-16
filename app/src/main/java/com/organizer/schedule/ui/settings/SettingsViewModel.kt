package com.organizer.schedule.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.domain.repository.ScheduleRepository
import com.organizer.schedule.domain.usecase.ExportScheduleToIcsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * ViewModel экрана настроек (тема, Dynamic Color, уведомления, первый день недели и экспорт .ics).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val exportScheduleToIcsUseCase: ExportScheduleToIcsUseCase
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

    fun setNotificationSettings(enabled: Boolean, minutesBefore: Int) {
        viewModelScope.launch { repository.updateNotificationSettings(enabled, minutesBefore) }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        viewModelScope.launch { repository.updateFirstDayOfWeek(day) }
    }

    suspend fun buildScheduleIcsContent(): String {
        val lessons = repository.observeAllLessons().first()
        return exportScheduleToIcsUseCase(lessons)
    }
}
