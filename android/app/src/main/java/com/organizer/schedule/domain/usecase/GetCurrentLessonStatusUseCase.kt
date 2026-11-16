package com.organizer.schedule.domain.usecase

import com.organizer.schedule.domain.model.CurrentLessonStatus
import com.organizer.schedule.domain.model.Lesson
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

/**
 * Вычисляет прогресс текущего урока и время ожидания до следующего урока.
 */
class GetCurrentLessonStatusUseCase @Inject constructor() {

    operator fun invoke(
        todayLessons: List<Lesson>,
        now: LocalTime = LocalTime.now()
    ): CurrentLessonStatus {
        if (todayLessons.isEmpty()) return CurrentLessonStatus.NoLessonsToday

        val sorted = todayLessons.sortedBy { it.startTime }
        val activeLesson = sorted.firstOrNull { !now.isBefore(it.startTime) && now.isBefore(it.endTime) }
        val nextLesson = sorted.firstOrNull { it.startTime.isAfter(now) }

        if (activeLesson != null) {
            val totalSeconds = Duration.between(activeLesson.startTime, activeLesson.endTime)
                .seconds.coerceAtLeast(1L)
            val elapsedSeconds = Duration.between(activeLesson.startTime, now)
                .seconds.coerceIn(0L, totalSeconds)
            val progress = elapsedSeconds.toFloat() / totalSeconds.toFloat()
            val remainingMinutes = Duration.between(now, activeLesson.endTime)
                .toMinutes().coerceAtLeast(1L)

            val minutesUntilNext = nextLesson?.let {
                Duration.between(now, it.startTime).toMinutes().coerceAtLeast(1L)
            }

            return CurrentLessonStatus.Active(
                currentLesson = activeLesson,
                progress = progress,
                minutesRemaining = remainingMinutes,
                nextLesson = nextLesson,
                minutesUntilNext = minutesUntilNext
            )
        }

        if (nextLesson != null) {
            val minutesUntilNext = Duration.between(now, nextLesson.startTime)
                .toMinutes().coerceAtLeast(1L)
            return CurrentLessonStatus.Upcoming(
                nextLesson = nextLesson,
                minutesUntilNext = minutesUntilNext
            )
        }

        return CurrentLessonStatus.FinishedToday
    }
}
