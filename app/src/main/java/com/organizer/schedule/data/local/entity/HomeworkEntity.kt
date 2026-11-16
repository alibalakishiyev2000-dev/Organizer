package com.organizer.schedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Таблица домашних заданий с внешним ключом на таблицу уроков.
 * При удалении урока связанные задания удаляются каскадно.
 */
@Entity(
    tableName = "homework_tasks",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lessonId")]
)
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val lessonId: Long,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate,
    val isCompleted: Boolean = false
)
