package com.organizer.schedule.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.organizer.schedule.domain.model.AppThemeMode
import com.organizer.schedule.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "organizer_prefs")

/**
 * Хранилище пользовательских настроек на базе Jetpack DataStore.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val NOTIFICATION_MINUTES = intPreferencesKey("notification_minutes")
        val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val firstDayValue = prefs[Keys.FIRST_DAY_OF_WEEK] ?: DayOfWeek.MONDAY.value
        UserPreferences(
            themeMode = runCatching { AppThemeMode.valueOf(themeName) }
                .getOrDefault(AppThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATION_ENABLED] ?: true,
            notificationMinutesBefore = prefs[Keys.NOTIFICATION_MINUTES] ?: 15,
            firstDayOfWeek = DayOfWeek.of(firstDayValue)
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setNotificationSettings(enabled: Boolean, minutesBefore: Int) {
        context.dataStore.edit {
            it[Keys.NOTIFICATION_ENABLED] = enabled
            it[Keys.NOTIFICATION_MINUTES] = minutesBefore.coerceIn(5, 60)
        }
    }

    suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        context.dataStore.edit { it[Keys.FIRST_DAY_OF_WEEK] = day.value }
    }
}
