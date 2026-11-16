package com.organizer.schedule.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import kotlin.math.roundToInt

/**
 * Экран настроек: тема оформления (DayNight + OLED), Dynamic Color, уведомления, первый день недели и экспорт в .ics.
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onNotificationSettingsChange: (Boolean, Int) -> Unit,
    onFirstDayOfWeekChange: (DayOfWeek) -> Unit,
    onGenerateIcsContent: suspend () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Системный диалог сохранения файла .ics для импорта в Google Календарь
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = runCatching {
                val icsContent = onGenerateIcsContent()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(icsContent.toByteArray(Charsets.UTF_8))
                    } ?: error("Не удалось открыть поток записи")
                }
            }
            val message = if (result.isSuccess) {
                "Расписание сохранено в .ics для импорта в Google Календарь"
            } else {
                "Ошибка при экспорте: ${result.exceptionOrNull()?.localizedMessage}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Оформление (DayNight · OLED)", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.title) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Динамические цвета (Material You)",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "Адаптировать палитру под обои; тёмная тема использует чистый OLED-чёрный",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.dynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Уведомления об уроках", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Напоминать за ${preferences.notificationMinutesBefore} мин до начала",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                onNotificationSettingsChange(enabled, preferences.notificationMinutesBefore)
                            }
                        )
                    }

                    if (preferences.notificationsEnabled) {
                        Slider(
                            value = preferences.notificationMinutesBefore.toFloat(),
                            onValueChange = { value ->
                                onNotificationSettingsChange(true, (value / 5f).roundToInt() * 5)
                            },
                            valueRange = 5f..60f,
                            steps = 10
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(5, 10, 15, 30).forEach { mins ->
                                FilterChip(
                                    selected = preferences.notificationMinutesBefore == mins,
                                    onClick = { onNotificationSettingsChange(true, mins) },
                                    label = { Text("$mins мин") }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                NotificationHelper.showLessonReminder(
                                    context = context,
                                    lessonId = 9999L,
                                    subject = "Математический анализ",
                                    room = "Ауд. 304",
                                    startTimeText = "08:30",
                                    minutesBefore = preferences.notificationMinutesBefore
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Проверить уведомление (за ${preferences.notificationMinutesBefore} мин)")
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Календарь", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Первый день недели",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = preferences.firstDayOfWeek == DayOfWeek.MONDAY,
                            onClick = { onFirstDayOfWeekChange(DayOfWeek.MONDAY) },
                            label = { Text("Понедельник") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = preferences.firstDayOfWeek == DayOfWeek.SUNDAY,
                            onClick = { onFirstDayOfWeekChange(DayOfWeek.SUNDAY) },
                            label = { Text("Воскресенье") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Экспорт расписания", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Сохраните расписание в формате iCalendar (.ics) для быстрого импорта в Google Календарь с сохранением чётных/нечётных недель и номеров кабинетов.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { exportLauncher.launch("organizer_schedule.ics") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Экспорт")
                    }
                }
            }
        }
    }
}
