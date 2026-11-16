package com.organizer.schedule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.organizer.schedule.data.local.converter.RoomConverters
import com.organizer.schedule.data.local.dao.HomeworkDao
import com.organizer.schedule.data.local.dao.LessonDao
import com.organizer.schedule.data.local.entity.HomeworkEntity
import com.organizer.schedule.data.local.entity.LessonEntity

/**
 * Основная база данных Room приложения «Органайзер».
 */
@Database(
    entities = [LessonEntity::class, HomeworkEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class OrganizerDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun homeworkDao(): HomeworkDao
}
