package com.organizer.schedule.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.schedule.data.local.entity.LessonEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с расписанием уроков.
 */
@Dao
interface LessonDao {

    @Query("SELECT * FROM lessons ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeAll(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons ORDER BY dayOfWeek ASC, startTime ASC")
    suspend fun getAllOnce(): List<LessonEntity>

    @Query(
        """
        SELECT * FROM lessons 
        WHERE LOWER(subject) LIKE '%' || LOWER(:query) || '%' 
           OR LOWER(teacher) LIKE '%' || LOWER(:query) || '%' 
           OR LOWER(room) LIKE '%' || LOWER(:query) || '%'
        ORDER BY dayOfWeek ASC, startTime ASC
        """
    )
    fun search(query: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getById(id: Long): LessonEntity?

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lesson: LessonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lessons: List<LessonEntity>): List<Long>

    @Delete
    suspend fun delete(lesson: LessonEntity)
}
