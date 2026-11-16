package com.organizer.schedule.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.organizer.schedule.domain.model.Lesson
import com.organizer.schedule.domain.model.RecurrenceType
import com.organizer.schedule.ui.homework.HomeworkScreen
import com.organizer.schedule.ui.homework.HomeworkViewModel
import com.organizer.schedule.ui.schedule.LessonEditorSheet
import com.organizer.schedule.ui.schedule.ScheduleScreen
import com.organizer.schedule.ui.schedule.ScheduleViewModel
import com.organizer.schedule.ui.settings.SettingsScreen
import com.organizer.schedule.ui.settings.SettingsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class OrganizerRoute(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    SCHEDULE("schedule", "Расписание", Icons.Rounded.CalendarMonth),
    HOMEWORK("homework", "Задания", Icons.Rounded.Assignment),
    SETTINGS("settings", "Настройки", Icons.Rounded.Settings)
}

/**
 * Корневая навигация приложения: верхняя панель, нижнее меню с плавными переходами, FAB «+» и редактор уроков.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerNavHost(
    scheduleViewModel: ScheduleViewModel = hiltViewModel(),
    homeworkViewModel: HomeworkViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()
    val homeworkState by homeworkViewModel.uiState.collectAsStateWithLifecycle()
    val preferences by settingsViewModel.preferences.collectAsStateWithLifecycle()

    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var editorVisible by remember { mutableStateOf(false) }
    var editingLesson by remember { mutableStateOf<Lesson?>(null) }

    val activeDate = scheduleState.weekDays.getOrNull(selectedDayIndex) ?: LocalDate.now()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM", Locale("ru")) }
    val weekParityLabel = if (scheduleState.selectedEvenWeek) "Верхняя неделя" else "Нижняя неделя"

    val isScheduleTab = currentDestination?.route == OrganizerRoute.SCHEDULE.route || currentDestination == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (currentDestination?.route) {
                                OrganizerRoute.HOMEWORK.route -> "Домашние задания"
                                OrganizerRoute.SETTINGS.route -> "Настройки"
                                else -> weekParityLabel
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (isScheduleTab) {
                            Text(
                                text = "Сегодня, ${LocalDate.now().format(dateFormatter)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (isScheduleTab) {
                        IconButton(
                            onClick = {
                                scheduleViewModel.onSearchActiveChange(!scheduleState.isSearchActive)
                            }
                        ) {
                            Icon(
                                imageVector = if (scheduleState.isSearchActive) {
                                    Icons.Rounded.Close
                                } else {
                                    Icons.Rounded.Search
                                },
                                contentDescription = "Поиск по предметам"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isScheduleTab,
                enter = scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180)),
                exit = scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(160)
                ) + fadeOut(tween(140))
            ) {
                FloatingActionButton(
                    onClick = {
                        editingLesson = null
                        editorVisible = true
                    }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Добавить урок")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                OrganizerRoute.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = OrganizerRoute.SCHEDULE.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 16 } +
                    scaleIn(initialScale = 0.98f, animationSpec = tween(260, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it / 16 }
            },
            popEnterTransition = {
                fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 16 } +
                    scaleIn(initialScale = 0.98f, animationSpec = tween(260, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 16 }
            }
        ) {
            composable(OrganizerRoute.SCHEDULE.route) {
                ScheduleScreen(
                    uiState = scheduleState,
                    selectedDateIndex = selectedDayIndex,
                    onSelectedDateIndexChange = { selectedDayIndex = it },
                    onSelectedWeekParityChange = scheduleViewModel::onSelectedWeekParityChange,
                    onSearchQueryChange = scheduleViewModel::onSearchQueryChange,
                    onToggleHomework = scheduleViewModel::toggleHomework,
                    onEditLesson = { lesson ->
                        editingLesson = lesson
                        editorVisible = true
                    }
                )
            }
            composable(OrganizerRoute.HOMEWORK.route) {
                HomeworkScreen(
                    uiState = homeworkState,
                    onToggleCompleted = homeworkViewModel::toggleCompleted,
                    onAddHomework = homeworkViewModel::addHomework,
                    onDeleteHomework = homeworkViewModel::deleteHomework
                )
            }
            composable(OrganizerRoute.SETTINGS.route) {
                SettingsScreen(
                    preferences = preferences,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    onDynamicColorChange = settingsViewModel::setDynamicColor,
                    onUseSystemFontChange = settingsViewModel::setUseSystemFont,
                    onHighRefreshRateChange = settingsViewModel::setHighRefreshRate,
                    onNotificationSettingsChange = settingsViewModel::setNotificationSettings,
                    onFirstDayOfWeekChange = settingsViewModel::setFirstDayOfWeek,
                    onGenerateIcsContent = settingsViewModel::buildScheduleIcsContent,
                    onGenerateJsonBackup = settingsViewModel::buildScheduleJsonBackup,
                    onGenerateBaseJsonBackup = settingsViewModel::buildBaseScheduleJsonBackup,
                    onParseImportBackup = settingsViewModel::parseScheduleBackup,
                    onImportParsedSchedule = settingsViewModel::importParsedSchedule
                )
            }
        }
    }

    if (editorVisible) {
        val defaultRecurrence = if (scheduleState.selectedEvenWeek != scheduleState.isCurrentWeekEven) {
            if (scheduleState.selectedEvenWeek) RecurrenceType.EVEN_WEEK else RecurrenceType.ODD_WEEK
        } else {
            RecurrenceType.WEEKLY
        }
        LessonEditorSheet(
            initialLesson = editingLesson,
            defaultDate = activeDate,
            defaultRecurrence = defaultRecurrence,
            onDismiss = { editorVisible = false },
            onSave = { saved ->
                scheduleViewModel.saveLesson(saved)
                editorVisible = false
            },
            onDelete = { toDelete ->
                scheduleViewModel.deleteLesson(toDelete)
                editorVisible = false
            }
        )
    }
}
