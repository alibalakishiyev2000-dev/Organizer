package com.organizer.schedule.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.domain.model.SubjectColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Нижний лист добавления и редактирования урока с выбором времени через Material 3 TimePicker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonEditorSheet(
    initialLesson: Lesson?,
    defaultDate: LocalDate,
    defaultRecurrence: RecurrenceType = RecurrenceType.WEEKLY,
    onDismiss: () -> Unit,
    onSave: (Lesson) -> Unit,
    onDelete: ((Lesson) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val isDark = isSystemInDarkTheme()

    var subject by remember(initialLesson) { mutableStateOf(initialLesson?.subject ?: "") }
    var teacher by remember(initialLesson) { mutableStateOf(initialLesson?.teacher ?: "") }
    var room by remember(initialLesson) { mutableStateOf(initialLesson?.room ?: "") }
    var notes by remember(initialLesson) { mutableStateOf(initialLesson?.notes ?: "") }
    var selectedDay by remember(initialLesson) {
        mutableStateOf(initialLesson?.dayOfWeek ?: defaultDate.dayOfWeek)
    }
    var startTime by remember(initialLesson) {
        mutableStateOf(initialLesson?.startTime ?: LocalTime.of(9, 0))
    }
    var endTime by remember(initialLesson) {
        mutableStateOf(initialLesson?.endTime ?: LocalTime.of(10, 30))
    }
    var selectedColor by remember(initialLesson) {
        mutableStateOf(initialLesson?.color ?: SubjectColor.SKY)
    }
    var selectedRecurrence by remember(initialLesson, defaultRecurrence) {
        mutableStateOf(initialLesson?.recurrence ?: defaultRecurrence)
    }

    var pickingStartTime by remember { mutableStateOf(false) }
    var pickingEndTime by remember { mutableStateOf(false) }
    var subjectError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialLesson == null) "Новый урок" else "Редактировать урок",
                    style = MaterialTheme.typography.titleLarge
                )
                if (initialLesson != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initialLesson) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Удалить урок",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = subject,
                onValueChange = {
                    subject = it
                    subjectError = false
                },
                label = { Text("Название предмета *") },
                isError = subjectError,
                supportingText = if (subjectError) {
                    { Text("Введите название предмета") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Номер кабинета") },
                    placeholder = { Text("Напр. 304") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Преподаватель") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1.35f)
                )
            }

            // Выбор времени начала и окончания урока через Material TimePicker
            Text("Время занятия", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { pickingStartTime = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Начало: ${startTime.format(timeFormatter)}")
                }
                OutlinedButton(
                    onClick = { pickingEndTime = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Конец: ${endTime.format(timeFormatter)}")
                }
            }

            // День недели
            Text("День недели", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    val label = day.getDisplayName(TextStyle.SHORT, Locale("ru"))
                        .replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        label = { Text(label) }
                    )
                }
            }

            // Повторение
            Text("Повторение", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RecurrenceType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedRecurrence == type,
                        onClick = { selectedRecurrence = type },
                        label = { Text(type.title) }
                    )
                }
            }

            // Выбор пастельного цвета предмета
            Text("Цвет предмета", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SubjectColor.entries.forEach { colorOption ->
                    val swatchColor = Color(if (isDark) colorOption.darkHex else colorOption.lightHex)
                    val borderColor = Color(colorOption.accentHex)
                    val isSelected = selectedColor == colorOption
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) borderColor else borderColor.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .clickable { selectedColor = colorOption },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = colorOption.title,
                                tint = borderColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Заметка к уроку") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    if (subject.isBlank()) {
                        subjectError = true
                        return@Button
                    }
                    val safeEndTime = if (endTime.isAfter(startTime)) endTime else startTime.plusMinutes(45)
                    onSave(
                        Lesson(
                            id = initialLesson?.id ?: 0L,
                            subject = subject.trim(),
                            teacher = teacher.trim(),
                            room = room.trim(),
                            dayOfWeek = selectedDay,
                            startTime = startTime,
                            endTime = safeEndTime,
                            color = selectedColor,
                            recurrence = selectedRecurrence,
                            specificDate = if (selectedRecurrence == RecurrenceType.ONCE) {
                                initialLesson?.specificDate ?: defaultDate
                            } else null,
                            notes = notes.trim()
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (initialLesson == null) "Добавить в расписание" else "Сохранить изменения")
            }
        }
    }

    if (pickingStartTime) {
        MaterialTimePickerDialog(
            title = "Время начала",
            initialTime = startTime,
            onDismiss = { pickingStartTime = false },
            onConfirm = { picked ->
                startTime = picked
                if (!endTime.isAfter(picked)) {
                    endTime = picked.plusMinutes(45)
                }
                pickingStartTime = false
            }
        )
    }

    if (pickingEndTime) {
        MaterialTimePickerDialog(
            title = "Время окончания",
            initialTime = endTime,
            onDismiss = { pickingEndTime = false },
            onConfirm = { picked ->
                endTime = picked
                pickingEndTime = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialTimePickerDialog(
    title: String,
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("Выбрать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
