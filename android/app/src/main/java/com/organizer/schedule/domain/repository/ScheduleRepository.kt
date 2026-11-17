package com.organizer.schedule.domain.repository

import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.ScheduleImportResult
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.domain.model.WeekParityMode
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

/**
 * Контракт репозитория расписания, домашних заданий и настроек.
 */
interface ScheduleRepository {
    fun observeAllLessons(): Flow<List<Lesson>>
    fun observeLessonsByQuery(query: String): Flow<List<Lesson>>
    suspend fun getLessonById(id: Long): Lesson?
    suspend fun upsertLesson(lesson: Lesson): Long
    suspend fun deleteLesson(lesson: Lesson)

    fun observeAllHomework(): Flow<List<HomeworkTask>>
    fun observeHomeworkForLesson(lessonId: Long): Flow<List<HomeworkTask>>
    suspend fun upsertHomework(task: HomeworkTask)
    suspend fun setHomeworkCompleted(id: Long, completed: Boolean)
    suspend fun deleteHomework(task: HomeworkTask)

    fun observePreferences(): Flow<UserPreferences>
    suspend fun updateThemeMode(mode: AppThemeMode)
    suspend fun updateDynamicColor(enabled: Boolean)
    suspend fun updateUseSystemFont(enabled: Boolean)
    suspend fun updateHighRefreshRate(enabled: Boolean)
    suspend fun updateNotificationSettings(enabled: Boolean, minutesBefore: Int)
    suspend fun updateFirstDayOfWeek(day: DayOfWeek)
    suspend fun updateWeekParityMode(mode: WeekParityMode)

    suspend fun importSchedule(
        lessons: List<Lesson>,
        homework: List<HomeworkTask>,
        replaceExisting: Boolean
    ): ScheduleImportResult

    fun getBaseScheduleSnapshot(): Pair<List<Lesson>, List<HomeworkTask>>

    suspend fun ensureSeeded()
}
