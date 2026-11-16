package com.organizer.schedule.di

import android.content.Context
import androidx.room.Room
import com.organizer.schedule.data.local.OrganizerDatabase
import com.organizer.schedule.data.local.dao.HomeworkDao
import com.organizer.schedule.data.local.dao.LessonDao
import com.organizer.schedule.data.repository.ScheduleRepositoryImpl
import com.organizer.schedule.domain.repository.ScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOrganizerDatabase(
        @ApplicationContext context: Context
    ): OrganizerDatabase = Room.databaseBuilder(
        context,
        OrganizerDatabase::class.java,
        "organizer_schedule.db"
    ).build()

    @Provides
    fun provideLessonDao(db: OrganizerDatabase): LessonDao = db.lessonDao()

    @Provides
    fun provideHomeworkDao(db: OrganizerDatabase): HomeworkDao = db.homeworkDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository
}
