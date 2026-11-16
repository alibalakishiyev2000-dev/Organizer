package com.organizer.schedule.ui.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeworkUiState(
    val tasks: List<HomeworkTask> = emptyList(),
    val lessons: List<Lesson> = emptyList()
)

/**
 * ViewModel экрана домашних заданий с привязкой к урокам.
 */
@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    val uiState: StateFlow<HomeworkUiState> = combine(
        repository.observeAllHomework(),
        repository.observeAllLessons()
    ) { tasks, lessons ->
        HomeworkUiState(
            tasks = tasks,
            lessons = lessons.distinctBy { it.subject to it.id }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeworkUiState()
    )

    fun toggleCompleted(task: HomeworkTask) {
        viewModelScope.launch {
            repository.setHomeworkCompleted(task.id, !task.isCompleted)
        }
    }

    fun addHomework(
        lessonId: Long,
        title: String,
        description: String,
        dueDate: LocalDate
    ) {
        viewModelScope.launch {
            repository.upsertHomework(
                HomeworkTask(
                    lessonId = lessonId,
                    title = title.trim(),
                    description = description.trim(),
                    dueDate = dueDate,
                    isCompleted = false
                )
            )
        }
    }

    fun deleteHomework(task: HomeworkTask) {
        viewModelScope.launch {
            repository.deleteHomework(task)
        }
    }
}
