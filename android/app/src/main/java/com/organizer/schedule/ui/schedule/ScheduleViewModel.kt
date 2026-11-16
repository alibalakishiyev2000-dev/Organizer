package com.organizer.schedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.schedule.domain.model.CurrentLessonStatus
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.repository.ScheduleRepository
import com.organizer.schedule.domain.usecase.GetCurrentLessonStatusUseCase
import com.organizer.schedule.domain.usecase.GetLessonsForDateUseCase
import com.organizer.schedule.notification.LessonAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ScheduleUiState(
    val weekDays: List<LocalDate> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val lessonsByDate: Map<LocalDate, List<Lesson>> = emptyMap(),
    val homeworkByLesson: Map<Long, List<HomeworkTask>> = emptyMap(),
    val currentStatus: CurrentLessonStatus = CurrentLessonStatus.NoLessonsToday,
    val isCurrentWeekEven: Boolean = false,
    val selectedEvenWeek: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
)

/**
 * ViewModel экрана расписания: управляет списком уроков, чётностью недели, прогрессом и поиском.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val getLessonsForDateUseCase: GetLessonsForDateUseCase,
    private val getCurrentLessonStatusUseCase: GetCurrentLessonStatusUseCase,
    private val alarmScheduler: LessonAlarmScheduler
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isSearchActive = MutableStateFlow(false)
    private val selectedEvenWeek = MutableStateFlow(getLessonsForDateUseCase.isEvenWeek(LocalDate.now()))

    // Поток времени обновляется каждые 30 секунд для живого прогресс-бара урока
    private val tickerFlow = flow {
        while (true) {
            emit(LocalTime.now())
            delay(30_000L)
        }
    }

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
        // Перепланируем уведомления при любом изменении расписания или настроек
        viewModelScope.launch {
            combine(
                repository.observeAllLessons(),
                repository.observePreferences()
            ) { lessons, prefs -> lessons to prefs }
                .collect { (lessons, prefs) ->
                    alarmScheduler.rescheduleUpcoming(
                        lessons = lessons,
                        enabled = prefs.notificationsEnabled,
                        minutesBefore = prefs.notificationMinutesBefore
                    )
                }
        }
    }

    val uiState: StateFlow<ScheduleUiState> = combine(
        repository.observeAllLessons(),
        repository.observeAllHomework(),
        repository.observePreferences(),
        searchQuery,
        isSearchActive,
        selectedEvenWeek,
        tickerFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allLessons = values[0] as List<Lesson>
        @Suppress("UNCHECKED_CAST")
        val allHomework = values[1] as List<HomeworkTask>
        val prefs = values[2] as com.organizer.schedule.domain.model.UserPreferences
        val query = values[3] as String
        val searchOpen = values[4] as Boolean
        val viewingEvenWeek = values[5] as Boolean
        val now = values[6] as LocalTime

        val today = LocalDate.now()
        val weekDays = buildWeekDates(today, prefs.firstDayOfWeek)

        val filtered = if (query.isBlank()) {
            allLessons
        } else {
            val q = query.trim().lowercase()
            allLessons.filter {
                it.subject.lowercase().contains(q) ||
                    it.teacher.lowercase().contains(q) ||
                    it.room.lowercase().contains(q)
            }
        }

        val lessonsByDate = weekDays.associateWith { date ->
            getLessonsForDateUseCase(filtered, date, evenWeekOverride = viewingEvenWeek)
        }

        val currentWeekEven = getLessonsForDateUseCase.isEvenWeek(today)
        val todayUnfilteredLessons = getLessonsForDateUseCase(allLessons, today, currentWeekEven)
        val status = getCurrentLessonStatusUseCase(todayUnfilteredLessons, now)

        ScheduleUiState(
            weekDays = weekDays,
            today = today,
            lessonsByDate = lessonsByDate,
            homeworkByLesson = allHomework.groupBy { it.lessonId },
            currentStatus = status,
            isCurrentWeekEven = currentWeekEven,
            selectedEvenWeek = viewingEvenWeek,
            searchQuery = query,
            isSearchActive = searchOpen,
            firstDayOfWeek = prefs.firstDayOfWeek
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ScheduleUiState(
            weekDays = buildWeekDates(LocalDate.now(), DayOfWeek.MONDAY),
            isCurrentWeekEven = getLessonsForDateUseCase.isEvenWeek(LocalDate.now()),
            selectedEvenWeek = getLessonsForDateUseCase.isEvenWeek(LocalDate.now())
        )
    )

    fun onSelectedWeekParityChange(isEven: Boolean) {
        selectedEvenWeek.value = isEven
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSearchActiveChange(active: Boolean) {
        isSearchActive.value = active
        if (!active) searchQuery.value = ""
    }

    fun saveLesson(lesson: Lesson) {
        viewModelScope.launch {
            repository.upsertLesson(lesson)
        }
    }

    fun deleteLesson(lesson: Lesson) {
        viewModelScope.launch {
            repository.deleteLesson(lesson)
        }
    }

    fun toggleHomework(task: HomeworkTask) {
        viewModelScope.launch {
            repository.setHomeworkCompleted(task.id, !task.isCompleted)
        }
    }

    private fun buildWeekDates(today: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
        val diff = (today.dayOfWeek.value - firstDay.value + 7) % 7
        val startOfWeek = today.minusDays(diff.toLong())
        return (0L..6L).map { startOfWeek.plusDays(it) }
    }
}
