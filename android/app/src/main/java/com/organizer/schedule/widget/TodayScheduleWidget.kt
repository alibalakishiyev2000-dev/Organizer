package com.organizer.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.organizer.schedule.MainActivity
import com.organizer.schedule.data.mapper.toDomain
import com.organizer.schedule.data.preferences.UserPreferencesDataStore
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.usecase.GetLessonsForDateUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Виджет рабочего стола «Уроки на сегодня» на базе Jetpack Glance (Material 3).
 */
class TodayScheduleWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun lessonDao(): com.organizer.schedule.data.local.dao.LessonDao
        fun getLessonsForDateUseCase(): GetLessonsForDateUseCase
        fun userPreferencesDataStore(): UserPreferencesDataStore
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val today = LocalDate.now()
        val prefs = entryPoint.userPreferencesDataStore().preferences.first()
        val allLessons = entryPoint.lessonDao().observeAll().first().map { it.toDomain() }
        val isCurrentWeekEven = entryPoint.getLessonsForDateUseCase().isEvenWeek(today, prefs.weekParityMode)
        val todayLessons = entryPoint.getLessonsForDateUseCase()(
            lessons = allLessons,
            targetDate = today,
            evenWeekOverride = isCurrentWeekEven
        )

        provideContent {
            GlanceTheme {
                WidgetContent(
                    today = today,
                    lessons = todayLessons,
                    isCurrentWeekEven = isCurrentWeekEven
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        today: LocalDate,
        lessons: List<Lesson>,
        isCurrentWeekEven: Boolean
    ) {
        val dateText = today.format(
            DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("ru"))
        ).replaceFirstChar { it.uppercase() }
        val weekLabel = if (isCurrentWeekEven) "Верхняя" else "Нижняя"
        val now = LocalTime.now()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сегодня · $dateText",
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = weekLabel,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            if (lessons.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "На сегодня занятий нет",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                ) {
                    items(lessons, itemId = { it.id }) { lesson ->
                        val isActive = !now.isBefore(lesson.startTime) && now.isBefore(lesson.endTime)
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) GlanceTheme.colors.primaryContainer
                                        else GlanceTheme.colors.surfaceVariant
                                    )
                                    .cornerRadius(14.dp)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .width(4.dp)
                                        .height(30.dp)
                                        .background(ColorProvider(Color(lesson.color.accentHex)))
                                        .cornerRadius(2.dp)
                                ) {}
                                Spacer(modifier = GlanceModifier.width(10.dp))
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = lesson.subject,
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Text(
                                        text = buildString {
                                            append("${lesson.startTime.format(timeFmt)}–${lesson.endTime.format(timeFmt)}")
                                            if (lesson.room.isNotBlank()) {
                                                append(" · ${lesson.room}")
                                            }
                                        },
                                        maxLines = 1,
                                        style = TextStyle(
                                            color = GlanceTheme.colors.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            runCatching {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(TodayScheduleWidget::class.java)
                ids.forEach { TodayScheduleWidget().update(context, it) }
            }
        }
    }
}

class TodayScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayScheduleWidget()
}
