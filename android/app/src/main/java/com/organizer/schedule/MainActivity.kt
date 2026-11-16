package com.organizer.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.organizer.schedule.ui.navigation.OrganizerNavHost
import com.organizer.schedule.ui.settings.SettingsViewModel
import com.organizer.schedule.ui.theme.OrganizerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs

/**
 * Главная Activity приложения «Органайзер» с поддержкой высокой частоты обновления до 120 Гц (120 fps).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyRefreshRatePreference(highRefreshRate = true)

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle()

            LaunchedEffect(prefs.highRefreshRate) {
                applyRefreshRatePreference(prefs.highRefreshRate)
            }

            // Запрос разрешения на уведомления для Android 13+ (API 33)
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = {}
            )
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            OrganizerTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                useSystemFont = prefs.useSystemFont
            ) {
                OrganizerNavHost()
            }
        }
    }

    /**
     * Выбирает режим экрана с высокой частотой обновления (до 120 Гц / 120 fps)
     * при сохранении текущего физического разрешения дисплея.
     */
    private fun applyRefreshRatePreference(highRefreshRate: Boolean) {
        runCatching {
            val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val currentMode = activeDisplay?.mode ?: return@runCatching
            val sameResolutionModes = activeDisplay.supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            if (sameResolutionModes.isEmpty()) return@runCatching

            val targetMode = if (highRefreshRate) {
                sameResolutionModes
                    .filter { it.refreshRate >= 119f }
                    .minByOrNull { it.refreshRate }
                    ?: sameResolutionModes.maxByOrNull { it.refreshRate }
            } else {
                sameResolutionModes.minByOrNull { abs(it.refreshRate - 60f) }
            } ?: return@runCatching

            val lp = window.attributes
            if (lp.preferredDisplayModeId != targetMode.modeId) {
                lp.preferredDisplayModeId = targetMode.modeId
                window.attributes = lp
            }
        }
    }
}
