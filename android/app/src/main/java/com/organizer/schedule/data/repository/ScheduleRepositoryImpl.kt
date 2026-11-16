package com.organizer.schedule.data.repository

import android.content.Context
import com.organizer.schedule.data.local.dao.HomeworkDao
import com.organizer.schedule.data.local.dao.LessonDao
import com.organizer.schedule.data.local.entity.HomeworkEntity
import com.organizer.schedule.data.local.entity.LessonEntity
import com.organizer.schedule.data.mapper.toDomain
import com.organizer.schedule.data.mapper.toEntity
import com.organizer.schedule.data.preferences.UserPreferencesDataStore
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.domain.model.SubjectColor
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.domain.repository.ScheduleRepository
import com.organizer.schedule.widget.TodayScheduleWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация репозитория расписания на базе Room + DataStore.
 */
@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lessonDao: LessonDao,
    private val homeworkDao: HomeworkDao,
    private val preferencesDataStore: UserPreferencesDataStore
) : ScheduleRepository {

    override fun observeAllLessons(): Flow<List<Lesson>> =
        lessonDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeLessonsByQuery(query: String): Flow<List<Lesson>> =
        if (query.isBlank()) {
            observeAllLessons()
        } else {
            lessonDao.search(query.trim()).map { entities -> entities.map { it.toDomain() } }
        }

    override suspend fun getLessonById(id: Long): Lesson? =
        lessonDao.getById(id)?.toDomain()

    override suspend fun upsertLesson(lesson: Lesson): Long {
        val id = lessonDao.upsert(lesson.toEntity())
        TodayScheduleWidget.refreshAll(context)
        return id
    }

    override suspend fun deleteLesson(lesson: Lesson) {
        lessonDao.delete(lesson.toEntity())
        TodayScheduleWidget.refreshAll(context)
    }

    override fun observeAllHomework(): Flow<List<HomeworkTask>> =
        homeworkDao.observeAllWithLesson().map { list -> list.map { it.toDomain() } }

    override fun observeHomeworkForLesson(lessonId: Long): Flow<List<HomeworkTask>> =
        homeworkDao.observeForLesson(lessonId).map { list -> list.map { it.toDomain() } }

    override suspend fun upsertHomework(task: HomeworkTask) {
        homeworkDao.upsert(task.toEntity())
    }

    override suspend fun setHomeworkCompleted(id: Long, completed: Boolean) {
        homeworkDao.setCompleted(id, completed)
    }

    override suspend fun deleteHomework(task: HomeworkTask) {
        homeworkDao.delete(task.toEntity())
    }

    override fun observePreferences(): Flow<UserPreferences> =
        preferencesDataStore.preferences

    override suspend fun updateThemeMode(mode: AppThemeMode) {
        preferencesDataStore.setThemeMode(mode)
    }

    override suspend fun updateDynamicColor(enabled: Boolean) {
        preferencesDataStore.setDynamicColor(enabled)
    }

    override suspend fun updateUseSystemFont(enabled: Boolean) {
        preferencesDataStore.setUseSystemFont(enabled)
    }

    override suspend fun updateHighRefreshRate(enabled: Boolean) {
        preferencesDataStore.setHighRefreshRate(enabled)
    }

    override suspend fun updateNotificationSettings(enabled: Boolean, minutesBefore: Int) {
        preferencesDataStore.setNotificationSettings(enabled, minutesBefore)
    }

    override suspend fun updateFirstDayOfWeek(day: DayOfWeek) {
        preferencesDataStore.setFirstDayOfWeek(day)
    }

    override suspend fun importSchedule(
        lessons: List<Lesson>,
        homework: List<HomeworkTask>,
        replaceExisting: Boolean
    ): com.organizer.schedule.domain.model.ScheduleImportResult {
        if (replaceExisting) {
            lessonDao.deleteAll()
        }

        val idMapping = mutableMapOf<Long, Long>()
        lessons.forEach { lesson ->
            val newId = lessonDao.upsert(lesson.copy(id = 0L).toEntity())
            idMapping[lesson.id] = newId
        }

        var importedHomeworkCount = 0
        homework.forEach { task ->
            val mappedLessonId = idMapping[task.lessonId] ?: return@forEach
            homeworkDao.upsert(task.copy(id = 0L, lessonId = mappedLessonId).toEntity())
            importedHomeworkCount++
        }

        TodayScheduleWidget.refreshAll(context)
        return com.organizer.schedule.domain.model.ScheduleImportResult(
            lessonsImported = lessons.size,
            homeworkImported = importedHomeworkCount
        )
    }

    /**
     * Заполняет базу примерами уроков и домашних заданий при первом запуске.
     */
    override suspend fun ensureSeeded() {
        if (lessonDao.count() > 0) return

        val today = LocalDate.now()
        val sampleLessons = listOf(
            LessonEntity(
                subject = "Математический анализ",
                teacher = "проф. Соколова Е.А.",
                room = "Ауд. 304",
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(10, 0),
                colorId = SubjectColor.SKY.id,
                recurrence = RecurrenceType.WEEKLY,
                notes = "Лекция по пределам и производным"
            ),
            LessonEntity(
                subject = "Алгоритмы и структуры данных",
                teacher = "доц. Волков Д.С.",
                room = "Комп. класс 12",
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(10, 15),
                endTime = LocalTime.of(11, 45),
                colorId = SubjectColor.SAGE.id,
                recurrence = RecurrenceType.WEEKLY,
                notes = "Практика на Kotlin: графы и деревья"
            ),
            LessonEntity(
                subject = "Английский язык",
                teacher = "Морозова А.В.",
                room = "Каб. 215",
                dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(12, 15),
                endTime = LocalTime.of(13, 45),
                colorId = SubjectColor.PEACH.id,
                recurrence = RecurrenceType.EVEN_WEEK
            ),
            LessonEntity(
                subject = "Физика",
                teacher = "доц. Громов П.Н.",
                room = "Лаб. 108",
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 30),
                colorId = SubjectColor.LAVENDER.id,
                recurrence = RecurrenceType.WEEKLY,
                notes = "Лабораторная работа №4"
            ),
            LessonEntity(
                subject = "Базы данных",
                teacher = "Орлов И.М.",
                room = "Ауд. 412",
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime.of(10, 45),
                endTime = LocalTime.of(12, 15),
                colorId = SubjectColor.MINT.id,
                recurrence = RecurrenceType.ODD_WEEK
            ),
            LessonEntity(
                subject = "История науки",
                teacher = "проф. Белова Н.К.",
                room = "Ауд. 201",
                dayOfWeek = DayOfWeek.WEDNESDAY,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(10, 0),
                colorId = SubjectColor.SAND.id,
                recurrence = RecurrenceType.WEEKLY
            ),
            LessonEntity(
                subject = "Мобильная разработка",
                teacher = "Тихонов А.Р.",
                room = "Технопарк 5",
                dayOfWeek = DayOfWeek.THURSDAY,
                startTime = LocalTime.of(10, 15),
                endTime = LocalTime.of(11, 45),
                colorId = SubjectColor.ROSE.id,
                recurrence = RecurrenceType.WEEKLY,
                notes = "Jetpack Compose & Material 3"
            ),
            LessonEntity(
                subject = "Линейная алгебра",
                teacher = "проф. Соколова Е.А.",
                room = "Ауд. 306",
                dayOfWeek = DayOfWeek.FRIDAY,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 30),
                colorId = SubjectColor.LEMON.id,
                recurrence = RecurrenceType.WEEKLY
            ),
            LessonEntity(
                subject = "Проектный семинар",
                teacher = "Тихонов А.Р.",
                room = "Коворкинг 2",
                dayOfWeek = today.dayOfWeek,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(15, 30),
                colorId = SubjectColor.SKY.id,
                recurrence = RecurrenceType.ONCE,
                specificDate = today,
                notes = "Разбор архитектуры MVVM + Room"
            )
        )

        val ids = lessonDao.insertAll(sampleLessons)
        val sampleHomework = listOf(
            HomeworkEntity(
                lessonId = ids[0],
                title = "Решить №14–18 из сборника Демидовича",
                description = "Тема: вычисление пределов по правилу Лопиталя",
                dueDate = today.plusDays(2),
                isCompleted = false
            ),
            HomeworkEntity(
                lessonId = ids[1],
                title = "Реализовать алгоритм Дейкстры",
                description = "С использованием PriorityQueue, покрыть юнит-тестами",
                dueDate = today.plusDays(3),
                isCompleted = false
            ),
            HomeworkEntity(
                lessonId = ids[3],
                title = "Оформить отчёт по лабораторной №3",
                description = "Графики погрешностей в миллиметрах",
                dueDate = today.plusDays(1),
                isCompleted = true
            ),
            HomeworkEntity(
                lessonId = ids[6],
                title = "Сверстать экран расписания на Compose",
                description = "Добавить HorizontalPager и TimePicker",
                dueDate = today.plusDays(4),
                isCompleted = false
            )
        )
        homeworkDao.insertAll(sampleHomework)
        TodayScheduleWidget.refreshAll(context)
    }
}
