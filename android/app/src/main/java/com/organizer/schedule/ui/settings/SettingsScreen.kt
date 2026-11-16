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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.ScheduleImportResult
import com.organizer.schedule.domain.model.UserPreferences
import com.organizer.schedule.domain.usecase.ParsedScheduleBackup
import com.organizer.schedule.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import kotlin.math.roundToInt

private const val MAX_IMPORT_FILE_BYTES = 5 * 1024 * 1024

/**
 * Экран настроек: тема оформления (DayNight + OLED), системный шрифт, 120 Гц, уведомления и импорт/экспорт расписания.
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onUseSystemFontChange: (Boolean) -> Unit,
    onHighRefreshRateChange: (Boolean) -> Unit,
    onNotificationSettingsChange: (Boolean, Int) -> Unit,
    onFirstDayOfWeekChange: (DayOfWeek) -> Unit,
    onGenerateIcsContent: suspend () -> String,
    onGenerateJsonBackup: suspend () -> String,
    onGenerateBaseJsonBackup: suspend () -> String,
    onParseImportBackup: suspend (String) -> ParsedScheduleBackup,
    onImportParsedSchedule: suspend (ParsedScheduleBackup, Boolean) -> ScheduleImportResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingImportBackup by remember { mutableStateOf<ParsedScheduleBackup?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // Экспорт в .ics для Google Календаря
    val exportIcsLauncher = rememberLauncherForActivityResult(
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

    // Экспорт полной резервной копии расписания и заданий в .json
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = runCatching {
                val jsonContent = onGenerateJsonBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonContent.toByteArray(Charsets.UTF_8))
                    } ?: error("Не удалось открыть поток записи")
                }
            }
            val message = if (result.isSuccess) {
                "Файл расписания успешно сохранён (.json)"
            } else {
                "Ошибка сохранения: ${result.exceptionOrNull()?.localizedMessage}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Экспорт базового стартового расписания в .json
    val exportBaseJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = runCatching {
                val jsonContent = onGenerateBaseJsonBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(jsonContent.toByteArray(Charsets.UTF_8))
                    } ?: error("Не удалось открыть поток записи")
                }
            }
            val message = if (result.isSuccess) {
                "Базовое расписание сохранено (.json)"
            } else {
                "Ошибка сохранения: ${result.exceptionOrNull()?.localizedMessage}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Импорт файла расписания (.json или .ics) с проверкой на фоновом потоке
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val parseResult = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytes(MAX_IMPORT_FILE_BYTES + 1)
                        if (bytes.size > MAX_IMPORT_FILE_BYTES) {
                            error("Файл слишком большой (максимум 5 МБ)")
                        }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("Не удалось прочитать файл")
                }
                onParseImportBackup(text)
            }
            parseResult.onSuccess { parsed ->
                pendingImportBackup = parsed
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.localizedMessage ?: "Не удалось распознать файл расписания",
                    Toast.LENGTH_LONG
                ).show()
            }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Системный шрифт",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Использовать системный шрифт интерфейса устройства",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.useSystemFont,
                            onCheckedChange = onUseSystemFontChange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Высокая частота (120 Гц)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Плавная прокрутка и анимации 120 fps на поддерживаемых экранах",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.highRefreshRate,
                            onCheckedChange = onHighRefreshRateChange
                        )
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Импорт и экспорт расписания", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Сохраните резервную копию (.json), импортируйте готовый файл расписания (.json или .ics) или экспортируйте в Google Календарь.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf("application/json", "text/calendar", "text/*", "*/*")
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Импорт")
                        }
                        Button(
                            onClick = { exportJsonLauncher.launch("organizer_backup.json") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Экспорт")
                        }
                    }
                    OutlinedButton(
                        onClick = { exportBaseJsonLauncher.launch("organizer_base_schedule.json") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Экспорт базового расписания (.json)")
                    }
                    OutlinedButton(
                        onClick = { exportIcsLauncher.launch("organizer_schedule.ics") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Экспорт в Google Календарь (.ics)")
                    }
                }
            }
        }

        item {
            Text(
                text = "Над приложением старался Али!",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp)
            )
        }
    }

    pendingImportBackup?.let { parsed ->
        val summaryText = buildString {
            append("Найдено уроков: ${parsed.lessons.size}")
            if (parsed.homework.isNotEmpty()) {
                append(", заданий: ${parsed.homework.size}")
            }
            append(". Заменить текущее расписание новыми уроками или добавить их к существующим?")
        }
        AlertDialog(
            onDismissRequest = { if (!isImporting) pendingImportBackup = null },
            title = { Text("Импорт расписания") },
            text = { Text(summaryText) },
            confirmButton = {
                Button(
                    enabled = !isImporting,
                    onClick = {
                        isImporting = true
                        pendingImportBackup = null
                        coroutineScope.launch {
                            val result = runCatching { onImportParsedSchedule(parsed, true) }
                            isImporting = false
                            val msg = result.fold(
                                onSuccess = {
                                    "Заменено: уроков ${it.lessonsImported}, заданий ${it.homeworkImported}"
                                },
                                onFailure = { "Ошибка импорта: ${it.localizedMessage}" }
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Заменить")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !isImporting,
                        onClick = { pendingImportBackup = null }
                    ) {
                        Text("Отмена")
                    }
                    TextButton(
                        enabled = !isImporting,
                        onClick = {
                            isImporting = true
                            pendingImportBackup = null
                            coroutineScope.launch {
                                val result = runCatching { onImportParsedSchedule(parsed, false) }
                                isImporting = false
                                val msg = result.fold(
                                    onSuccess = {
                                        "Добавлено: уроков ${it.lessonsImported}, заданий ${it.homeworkImported}"
                                    },
                                    onFailure = { "Ошибка импорта: ${it.localizedMessage}" }
                                )
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Text("Добавить")
                    }
                }
            }
        )
    }
}
