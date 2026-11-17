package com.organizer.schedule.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Корневая навигация приложения: верхняя панель, парящая панель навигации в стиле iOS, FAB «+» и редактор уроков.
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = OrganizerRoute.SCHEDULE.route,
                modifier = Modifier.fillMaxSize(),
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
                        onWeekParityModeChange = settingsViewModel::setWeekParityMode,
                        onGenerateIcsContent = settingsViewModel::buildScheduleIcsContent,
                        onGenerateJsonBackup = settingsViewModel::buildScheduleJsonBackup,
                        onGenerateBaseJsonBackup = settingsViewModel::buildBaseScheduleJsonBackup,
                        onParseImportBackup = settingsViewModel::parseScheduleBackup,
                        onImportParsedSchedule = settingsViewModel::importParsedSchedule
                    )
                }
            }

            // Плавающая кнопка добавления урока над капсулой навигации
            AnimatedVisibility(
                visible = isScheduleTab,
                enter = scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180)),
                exit = scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(160)
                ) + fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 94.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        editingLesson = null
                        editorVisible = true
                    },
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Добавить урок")
                }
            }

            // Плавающая нижняя панель навигации в стиле iOS поверх контента
            val selectedTabIndex = OrganizerRoute.entries
                .indexOfFirst { tab ->
                    currentDestination?.hierarchy?.any { it.route == tab.route } == true
                }
                .let { if (it >= 0) it else 0 }

            FloatingIosBottomNavBar(
                selectedIndex = selectedTabIndex,
                onSelectTab = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
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

/**
 * Современная плавающая панель навигации в стиле iOS:
 * капсула (CircleShape / 999dp), полупрозрачный стеклянный фон со светлой обводкой,
 * мягкой рассеянной тенью и скользящим полупрозрачным индикатором активной вкладки.
 */
@Composable
private fun FloatingIosBottomNavBar(
    selectedIndex: Int,
    onSelectTab: (OrganizerRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = OrganizerRoute.entries
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF20242D).copy(alpha = 0.88f),
                Color(0xFF14171D).copy(alpha = 0.84f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.88f),
                Color(0xFFF8FAFC).copy(alpha = 0.82f)
            )
        )
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.White.copy(alpha = 0.85f)
    }
    val inactiveTint = if (isDark) {
        Color(0xFFE2E8F0)
    } else {
        Color(0xFF1C1C1E)
    }
    val activePillBg = MaterialTheme.colorScheme.primary.copy(
        alpha = if (isDark) 0.24f else 0.14f
    )
    val activePillBorder = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.50f else 0.14f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.18f)
            )
            .clip(CircleShape)
            .background(glassGradient)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(6.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "iosNavPillOffset"
            )

            // Плавающий полупрозрачный pill-индикатор под выбранной вкладкой
            Surface(
                shape = CircleShape,
                color = activePillBg,
                border = BorderStroke(0.5.dp, activePillBorder),
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
            ) {}

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            inactiveTint
                        },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "iosNavTabTint"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectTab(tab) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = tab.label,
                            color = tint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
