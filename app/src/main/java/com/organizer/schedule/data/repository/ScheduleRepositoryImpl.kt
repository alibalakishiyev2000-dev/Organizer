package com.organizer.schedule.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.organizer.schedule.data.local.OrganizerDatabase
import com.organizer.schedule.data.local.dao.HomeworkDao
import com.organizer.schedule.data.local.dao.LessonDao
import com.organizer.schedule.data.mapper.toDomain
import com.organizer.schedule.data.mapper.toEntity
import com.organizer.schedule.data.preferences.UserPreferencesDataStore
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.domain.model.ScheduleImportResult
import com.organizer.schedule.domain.model.SubjectColor
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.domain.repository.ScheduleRepository
import com.organizer.schedule.widget.TodayScheduleWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    private val database: OrganizerDatabase,
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
    ): ScheduleImportResult = withContext(Dispatchers.IO) {
        if (lessons.isEmpty()) {
            return@withContext ScheduleImportResult(lessonsImported = 0, homeworkImported = 0)
        }

        val importedHomeworkCount = database.withTransaction {
            if (replaceExisting) {
                lessonDao.deleteAll()
            }

            val insertedIds = lessonDao.insertAll(lessons.map { it.copy(id = 0L).toEntity() })
            val idMapping = mutableMapOf<Long, Long>()
            lessons.forEachIndexed { index, lesson ->
                val newId = insertedIds.getOrNull(index) ?: return@forEachIndexed
                if (!idMapping.containsKey(lesson.id)) {
                    idMapping[lesson.id] = newId
                }
            }

            val mappedHomework = homework.mapNotNull { task ->
                val mappedLessonId = idMapping[task.lessonId] ?: return@mapNotNull null
                task.copy(id = 0L, lessonId = mappedLessonId).toEntity()
            }
            if (mappedHomework.isNotEmpty()) {
                homeworkDao.insertAll(mappedHomework)
            }
            mappedHomework.size
        }

        TodayScheduleWidget.refreshAll(context)
        ScheduleImportResult(
            lessonsImported = lessons.size,
            homeworkImported = importedHomeworkCount
        )
    }

    override fun getBaseScheduleSnapshot(): Pair<List<Lesson>, List<HomeworkTask>> {
        val today = LocalDate.now()
        val lessons = buildBaseLessons(today)
        val homework = buildBaseHomework(today, lessons.map { it.id })
        return lessons to homework
    }

    /**
     * Заполняет базу примерами уроков и домашних заданий при первом запуске.
     */
    override suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val seeded = database.withTransaction {
            if (lessonDao.count() > 0) return@withTransaction false

            val today = LocalDate.now()
            val sampleLessons = buildBaseLessons(today)
            val ids = lessonDao.insertAll(sampleLessons.map { it.copy(id = 0L).toEntity() })
            val sampleHomework = buildBaseHomework(today, ids)
            homeworkDao.insertAll(sampleHomework.map { it.copy(id = 0L).toEntity() })
            true
        }
        if (seeded) {
            TodayScheduleWidget.refreshAll(context)
        }
    }

    private fun buildBaseLessons(today: LocalDate): List<Lesson> = listOf(
        Lesson(
            id = 1L,
            subject = "Математический анализ",
            teacher = "проф. Соколова Е.А.",
            room = "Ауд. 304",
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            color = SubjectColor.SKY,
            recurrence = RecurrenceType.WEEKLY,
            notes = "Лекция по пределам и производным"
        ),
        Lesson(
            id = 2L,
            subject = "Алгоритмы и структуры данных",
            teacher = "доц. Волков Д.С.",
            room = "Комп. класс 12",
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(10, 15),
            endTime = LocalTime.of(11, 45),
            color = SubjectColor.SAGE,
            recurrence = RecurrenceType.WEEKLY,
            notes = "Практика на Kotlin: графы и деревья"
        ),
        Lesson(
            id = 3L,
            subject = "Английский язык",
            teacher = "Морозова А.В.",
            room = "Каб. 215",
            dayOfWeek = DayOfWeek.MONDAY,
            startTime = LocalTime.of(12, 15),
            endTime = LocalTime.of(13, 45),
            color = SubjectColor.PEACH,
            recurrence = RecurrenceType.EVEN_WEEK
        ),
        Lesson(
            id = 4L,
            subject = "Физика",
            teacher = "доц. Громов П.Н.",
            room = "Лаб. 108",
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            color = SubjectColor.LAVENDER,
            recurrence = RecurrenceType.WEEKLY,
            notes = "Лабораторная работа №4"
        ),
        Lesson(
            id = 5L,
            subject = "Базы данных",
            teacher = "Орлов И.М.",
            room = "Ауд. 412",
            dayOfWeek = DayOfWeek.TUESDAY,
            startTime = LocalTime.of(10, 45),
            endTime = LocalTime.of(12, 15),
            color = SubjectColor.MINT,
            recurrence = RecurrenceType.ODD_WEEK
        ),
        Lesson(
            id = 6L,
            subject = "История науки",
            teacher = "проф. Белова Н.К.",
            room = "Ауд. 201",
            dayOfWeek = DayOfWeek.WEDNESDAY,
            startTime = LocalTime.of(8, 30),
            endTime = LocalTime.of(10, 0),
            color = SubjectColor.SAND,
            recurrence = RecurrenceType.WEEKLY
        ),
        Lesson(
            id = 7L,
            subject = "Мобильная разработка",
            teacher = "Тихонов А.Р.",
            room = "Технопарк 5",
            dayOfWeek = DayOfWeek.THURSDAY,
            startTime = LocalTime.of(10, 15),
            endTime = LocalTime.of(11, 45),
            color = SubjectColor.ROSE,
            recurrence = RecurrenceType.WEEKLY,
            notes = "Jetpack Compose & Material 3"
        ),
        Lesson(
            id = 8L,
            subject = "Линейная алгебра",
            teacher = "проф. Соколова Е.А.",
            room = "Ауд. 306",
            dayOfWeek = DayOfWeek.FRIDAY,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            color = SubjectColor.LEMON,
            recurrence = RecurrenceType.WEEKLY
        ),
        Lesson(
            id = 9L,
            subject = "Проектный семинар",
            teacher = "Тихонов А.Р.",
            room = "Коворкинг 2",
            dayOfWeek = today.dayOfWeek,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(15, 30),
            color = SubjectColor.SKY,
            recurrence = RecurrenceType.ONCE,
            specificDate = today,
            notes = "Разбор архитектуры MVVM + Room"
        )
    )

    private fun buildBaseHomework(today: LocalDate, ids: List<Long>): List<HomeworkTask> {
        if (ids.size < 7) return emptyList()
        return listOf(
            HomeworkTask(
                id = 1L,
                lessonId = ids[0],
                title = "Решить №14–18 из сборника Демидовича",
                description = "Тема: вычисление пределов по правилу Лопиталя",
                dueDate = today.plusDays(2),
                isCompleted = false
            ),
            HomeworkTask(
                id = 2L,
                lessonId = ids[1],
                title = "Реализовать алгоритм Дейкстры",
                description = "С использованием PriorityQueue, покрыть юнит-тестами",
                dueDate = today.plusDays(3),
                isCompleted = false
            ),
            HomeworkTask(
                id = 3L,
                lessonId = ids[3],
                title = "Оформить отчёт по лабораторной №3",
                description = "Графики погрешностей в миллиметровке",
                dueDate = today.plusDays(1),
                isCompleted = true
            ),
            HomeworkTask(
                id = 4L,
                lessonId = ids[6],
                title = "Сверстать экран расписания на Compose",
                description = "Добавить HorizontalPager и TimePicker",
                dueDate = today.plusDays(4),
                isCompleted = false
            )
        )
    }
}
