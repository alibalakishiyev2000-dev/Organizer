package com.organizer.schedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.schedule.data.local.entity.HomeworkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Проекция домашнего задания с подтянутым названием и цветом предмета.
 */
data class HomeworkWithLesson(
    val id: Long,
    val lessonId: Long,
    val title: String,
    val description: String,
    val dueDate: java.time.LocalDate,
    val isCompleted: Boolean,
    val subject: String,
    val colorId: String
)

/**
 * DAO для работы с домашними заданиями.
 */
@Dao
interface HomeworkDao {

    @Query(
        """
        SELECT h.id, h.lessonId, h.title, h.description, h.dueDate, h.isCompleted,
               l.subject, l.colorId
        FROM homework_tasks h
        INNER JOIN lessons l ON h.lessonId = l.id
        ORDER BY h.isCompleted ASC, h.dueDate ASC
        """
    )
    fun observeAllWithLesson(): Flow<List<HomeworkWithLesson>>

    @Query(
        """
        SELECT h.id, h.lessonId, h.title, h.description, h.dueDate, h.isCompleted,
               l.subject, l.colorId
        FROM homework_tasks h
        INNER JOIN lessons l ON h.lessonId = l.id
        WHERE h.lessonId = :lessonId
        ORDER BY h.isCompleted ASC, h.dueDate ASC
        """
    )
    fun observeForLesson(lessonId: Long): Flow<List<HomeworkWithLesson>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: HomeworkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<HomeworkEntity>)

    @Query("UPDATE homework_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Delete
    suspend fun delete(task: HomeworkEntity)
}
