package com.alpwarestudio.chargefreeze

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.LocaleController
import com.alpwarestudio.chargefreeze.data.ThemeMode
import com.alpwarestudio.chargefreeze.ui.screens.DiagnosticsScreen
import com.alpwarestudio.chargefreeze.ui.screens.HomeScreen
import com.alpwarestudio.chargefreeze.ui.screens.MainViewModel
import com.alpwarestudio.chargefreeze.ui.screens.SettingsScreen
import com.alpwarestudio.chargefreeze.ui.theme.ChargeFreezeTheme

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences(this)

        setContent {
            var screen by remember { mutableStateOf(AppScreen.HOME) }
            var themeMode by remember { mutableStateOf(preferences.themeMode) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current

            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            ChargeFreezeTheme(themeMode) {
                when (screen) {
                    AppScreen.HOME -> HomeScreen(vm, onSettings = { screen = AppScreen.SETTINGS })
                    AppScreen.SETTINGS -> SettingsScreen(
                        vm = vm,
                        preferences = preferences,
                        themeMode = themeMode,
                        onThemeChanged = { mode: ThemeMode ->
                            preferences.themeMode = mode
                            themeMode = mode
                        },
                        onLanguageChanged = {
                            preferences.languageMode = it
                            recreate()
                        },
                        onBack = { screen = AppScreen.HOME },
                        onDiagnostics = { screen = AppScreen.DIAGNOSTICS }
                    )
                    AppScreen.DIAGNOSTICS -> DiagnosticsScreen(vm = vm, onBack = { screen = AppScreen.SETTINGS })
                }
            }
        }

    }
}

private enum class AppScreen { HOME, SETTINGS, DIAGNOSTICS }
