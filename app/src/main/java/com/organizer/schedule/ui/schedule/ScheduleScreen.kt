package com.organizer.schedule.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.organizer.schedule.domain.model.CurrentLessonStatus
import com.organizer.schedule.domain.model.HomeworkTask
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * Экран недельного расписания с горизонтальным свайпом Пн–Вс, поиском и живым статусом урока.
 */
@Composable
fun ScheduleScreen(
    uiState: ScheduleUiState,
    selectedDateIndex: Int,
    onSelectedDateIndexChange: (Int) -> Unit,
    onSelectedWeekParityChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleHomework: (HomeworkTask) -> Unit,
    onEditLesson: (Lesson) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = uiState.weekDays
    if (weekDays.isEmpty()) return

    val todayIndex = remember(weekDays, uiState.today) {
        weekDays.indexOf(uiState.today).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = todayIndex,
        pageCount = { weekDays.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onSelectedDateIndexChange(pagerState.currentPage)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Переключатель чётной и нечётной недели для просмотра и редактирования
        WeekParitySwitcher(
            selectedEvenWeek = uiState.selectedEvenWeek,
            isCurrentWeekEven = uiState.isCurrentWeekEven,
            onSelectParity = onSelectedWeekParityChange
        )

        // Поле поиска по предметам, кабинетам и преподавателям
        AnimatedVisibility(
            visible = uiState.isSearchActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Поиск предмета, кабинета или преподавателя") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Полоса дней недели Пн–Вс с подсветкой сегодняшнего дня и плавным переключением
        WeekDaySelectorRow(
            weekDays = weekDays,
            today = uiState.today,
            selectedIndex = pagerState.currentPage,
            onSelectDay = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                    )
                }
            }
        )

        // Горизонтальный свайп по дням недели с плавным масштабом и прозрачностью при смене вкладки
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageOffset = ((pagerState.currentPage - pageIndex) +
                pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val pageScale = 1f - (0.04f * pageOffset)
            val pageAlpha = 1f - (0.28f * pageOffset)

            val date = weekDays[pageIndex]
            val dayLessons = uiState.lessonsByDate[date].orEmpty()
            val isTodayPage = date == uiState.today &&
                uiState.selectedEvenWeek == uiState.isCurrentWeekEven

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pageScale
                        scaleY = pageScale
                        alpha = pageAlpha
                    },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 136.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isTodayPage) {
                    item(key = "live_status_banner") {
                        LiveStatusCard(status = uiState.currentStatus)
                    }
                }

                if (dayLessons.isEmpty()) {
                    item(key = "empty_day") {
                        EmptyDayState(
                            isFiltered = uiState.searchQuery.isNotBlank()
                        )
                    }
                } else {
                    items(
                        items = dayLessons,
                        key = { lesson -> "${date}_${uiState.selectedEvenWeek}_${lesson.id}" }
                    ) { lesson ->
                        val isCurrent = isTodayPage &&
                            (uiState.currentStatus as? CurrentLessonStatus.Active)
                                ?.currentLesson?.id == lesson.id

                        LessonCard(
                            lesson = lesson,
                            isCurrent = isCurrent,
                            linkedHomework = uiState.homeworkByLesson[lesson.id].orEmpty(),
                            onToggleHomework = onToggleHomework,
                            onEdit = { onEditLesson(lesson) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekParitySwitcher(
    selectedEvenWeek: Boolean,
    isCurrentWeekEven: Boolean,
    onSelectParity: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            true to if (isCurrentWeekEven) "Верхняя (текущая)" else "Верхняя неделя",
            false to if (!isCurrentWeekEven) "Нижняя (текущая)" else "Нижняя неделя"
        )
        options.forEach { (isEven, label) ->
            val selected = selectedEvenWeek == isEven
            val bgColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "parityBg"
            )
            val fgColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "parityFg"
            )
            Surface(
                color = bgColor,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectParity(isEven) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = fgColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDaySelectorRow(
    weekDays: List<LocalDate>,
    today: LocalDate,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekDays.forEachIndexed { index, date ->
            val isSelected = index == selectedIndex
            val isToday = date == today
            val dayShort = date.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale("ru"))
                .replaceFirstChar { it.uppercase() }

            val containerColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "dayContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "dayContent"
            )
            val tabScale by animateFloatAsState(
                targetValue = if (isSelected) 1.04f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "dayScale"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = tabScale
                        scaleY = tabScale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .clickable { onSelectDay(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayShort,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                if (isToday && !isSelected) {
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStatusCard(status: CurrentLessonStatus) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (status) {
                is CurrentLessonStatus.Active -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Сейчас идёт урок",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Осталось ${status.minutesRemaining} мин",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Text(
                        text = status.currentLesson.subject,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(
                        progress = { status.progress },
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    status.nextLesson?.let { next ->
                        Text(
                            text = "Следующий урок через ${status.minutesUntilNext} мин: ${next.subject} (${next.startTime.format(timeFmt)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                is CurrentLessonStatus.Upcoming -> {
                    Text(
                        text = "Следующий урок через ${formatMinutesRu(status.minutesUntilNext)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${status.nextLesson.subject} · ${status.nextLesson.startTime.format(timeFmt)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (status.nextLesson.room.isNotBlank()) {
                        Text(
                            text = status.nextLesson.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                CurrentLessonStatus.FinishedToday -> {
                    Text(
                        text = "Уроки на сегодня завершены",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Отличная работа! Проверьте домашние задания на завтра.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                CurrentLessonStatus.NoLessonsToday -> {
                    Text(
                        text = "Сегодня выходной",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "В расписании на этот день нет занятий.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    isCurrent: Boolean,
    linkedHomework: List<HomeworkTask>,
    onToggleHomework: (HomeworkTask) -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val cardBg = Color(if (isDark) lesson.color.darkHex else lesson.color.lightHex)
    val stripeColor = Color(lesson.color.accentHex)

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isCurrent) 4.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Цветная полоса предмета слева
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(stripeColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = stripeColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${lesson.startTime.format(timeFormatter)} – ${lesson.endTime.format(timeFormatter)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (lesson.recurrence != RecurrenceType.WEEKLY) {
                            Text(
                                text = lesson.recurrence.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = stripeColor,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "Детали урока",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = if (lesson.room.isBlank()) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEdit() }
                                .padding(vertical = 2.dp)
                        } else Modifier
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = if (lesson.room.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = lesson.room.ifBlank { "Указать кабинет" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lesson.room.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    if (lesson.teacher.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lesson.teacher,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Раскрывающийся блок с заметками, заданиями и кнопкой редактирования
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(150))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = lesson.recurrence.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (lesson.notes.isNotBlank()) {
                            Text(
                                text = lesson.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (linkedHomework.isNotEmpty()) {
                            Text(
                                text = "Домашние задания (${linkedHomework.count { !it.isCompleted }} активных)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            linkedHomework.forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onToggleHomework(task) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (task.isCompleted) {
                                            Icons.Rounded.CheckCircle
                                        } else {
                                            Icons.Rounded.RadioButtonUnchecked
                                        },
                                        contentDescription = null,
                                        tint = if (task.isCompleted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                        color = if (task.isCompleted) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Изменить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDayState(isFiltered: Boolean) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isFiltered) "Ничего не найдено" else "Свободный день",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isFiltered) {
                    "Попробуйте изменить поисковый запрос."
                } else {
                    "Нажмите «+», чтобы добавить урок на этот день."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMinutesRu(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "$hours ч $mins мин"
        hours > 0 -> "$hours ч"
        else -> "$mins мин"
    }
}
