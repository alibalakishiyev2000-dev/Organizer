package com.organizer.schedule.data.mapper

import com.organizer.schedule.data.local.dao.HomeworkWithLesson
import com.organizer.schedule.data.local.entity.HomeworkEntity
import com.organizer.schedule.data.local.entity.LessonEntity
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.SubjectColor

fun LessonEntity.toDomain(): Lesson = Lesson(
    id = id,
    subject = subject,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    color = SubjectColor.fromId(colorId),
    recurrence = recurrence,
    specificDate = specificDate,
    notes = notes
)

fun Lesson.toEntity(): LessonEntity = LessonEntity(
    id = id,
    subject = subject,
    teacher = teacher,
    room = room,
    dayOfWeek = dayOfWeek,
    startTime = startTime,
    endTime = endTime,
    colorId = color.id,
    recurrence = recurrence,
    specificDate = specificDate,
    notes = notes
)

fun HomeworkWithLesson.toDomain(): HomeworkTask = HomeworkTask(
    id = id,
    lessonId = lessonId,
    subjectName = subject,
    subjectColor = SubjectColor.fromId(colorId),
    title = title,
    description = description,
    dueDate = dueDate,
    isCompleted = isCompleted
)

fun HomeworkTask.toEntity(): HomeworkEntity = HomeworkEntity(
    id = id,
    lessonId = lessonId,
    title = title,
    description = description,
    dueDate = dueDate,
    isCompleted = isCompleted
)
