package com.alpwarestudio.chargefreeze

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alpwarestudio.chargefreeze.data.AppPreferences
import com.alpwarestudio.chargefreeze.data.LanguageMode
import com.alpwarestudio.chargefreeze.data.LocaleController
import com.alpwarestudio.chargefreeze.data.ThemeMode
import com.alpwarestudio.chargefreeze.ui.screens.DiagnosticsScreen
import com.alpwarestudio.chargefreeze.ui.screens.HomeScreen
import com.alpwarestudio.chargefreeze.ui.screens.MainViewModel
import com.alpwarestudio.chargefreeze.ui.screens.SettingsScreen
import com.alpwarestudio.chargefreeze.ui.theme.ChargeFreezeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = AppPreferences(this)
        applyWindowBackground(preferences.themeMode)

        setContent {
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
                val windowBackground = MaterialTheme.colorScheme.background

                SideEffect {
                    // Keep the platform window identical to the Compose background. This layer
                    // can become visible for a frame while destinations move or fade.
                    window.setBackgroundDrawable(windowBackground.toArgb().toDrawable())
                }

                AppNavigation(
                    vm = vm,
                    preferences = preferences,
                    themeMode = themeMode,
                    onThemeChanged = { mode ->
                        preferences.themeMode = mode
                        themeMode = mode
                        applyWindowBackground(mode)
                    },
                    onLanguageChanged = {
                        preferences.languageMode = it
                        recreate()
                    }
                )
            }
        }
    }

    private fun applyWindowBackground(mode: ThemeMode) {
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        window.setBackgroundDrawable(
            (if (dark) Color.rgb(16, 20, 24) else Color.rgb(247, 249, 252).toDrawable()) as Drawable?
        )
    }
}

@Composable
private fun AppNavigation(
    vm: MainViewModel,
    preferences: AppPreferences,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onLanguageChanged: (LanguageMode) -> Unit
) {
    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    val screen = AppScreen.entries.firstOrNull { it.name == screenName } ?: AppScreen.HOME
    val backProgress = remember { Animatable(0f) }
    val animationScope = rememberCoroutineScope()
    val previousScreen by rememberUpdatedState(screen.parent)
    var predictiveCommitInProgress by remember { mutableStateOf(false) }

    fun navigate(destination: AppScreen) {
        screenName = destination.name
    }

    fun navigateBack() {
        screen.parent?.let { screenName = it.name }
    }

    PredictiveBackHandler(enabled = screen.parent != null) { events ->
        try {
            events.collect { event ->
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            if (previousScreen != null) {
                predictiveCommitInProgress = true
                navigateBack()
            }
            backProgress.snapTo(0f)
        } catch (_: CancellationException) {
            animationScope.launch {
                backProgress.animateTo(0f, tween(120))
            }
        }
    }

    LaunchedEffect(screenName) {
        backProgress.snapTo(0f)
        if (predictiveCommitInProgress) predictiveCommitInProgress = false
    }

    val gestureProgress = backProgress.value

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (gestureProgress > 0.001f) {
            screen.parent?.let { destination ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -size.width * 0.06f * (1f - gestureProgress)
                        }
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AppScreenContent(
                        screen = destination,
                        vm = vm,
                        preferences = preferences,
                        themeMode = themeMode,
                        onThemeChanged = onThemeChanged,
                        onLanguageChanged = onLanguageChanged,
                        onNavigate = ::navigate,
                        onBack = ::navigateBack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AnimatedContent(
            targetState = screen,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (gestureProgress > 0f) {
                        translationX = size.width * gestureProgress
                    }
                },
            transitionSpec = {
                if (predictiveCommitInProgress) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val goingBack = targetState.depth < initialState.depth
                    val enter = if (goingBack) {
                        slideInHorizontally(tween(240)) { -it / 10 } + fadeIn(tween(180))
                    } else {
                        slideInHorizontally(tween(260)) { it / 10 } + fadeIn(tween(200))
                    }
                    val exit = if (goingBack) {
                        slideOutHorizontally(tween(240)) { it / 6 } + fadeOut(tween(160))
                    } else {
                        slideOutHorizontally(tween(220)) { -it / 12 } + fadeOut(tween(160))
                    }
                    enter togetherWith exit
                }
            },
            label = "app-screen"
        ) { destination ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AppScreenContent(
                    screen = destination,
                    vm = vm,
                    preferences = preferences,
                    themeMode = themeMode,
                    onThemeChanged = onThemeChanged,
                    onLanguageChanged = onLanguageChanged,
                    onNavigate = ::navigate,
                    onBack = ::navigateBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AppScreenContent(
    screen: AppScreen,
    vm: MainViewModel,
    preferences: AppPreferences,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onLanguageChanged: (LanguageMode) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    Box(modifier) {
        when (screen) {
            AppScreen.HOME -> HomeScreen(vm, onSettings = { onNavigate(AppScreen.SETTINGS) })
            AppScreen.SETTINGS -> SettingsScreen(
                vm = vm,
                preferences = preferences,
                themeMode = themeMode,
                onThemeChanged = onThemeChanged,
                onLanguageChanged = onLanguageChanged,
                onBack = onBack,
                onDiagnostics = { onNavigate(AppScreen.DIAGNOSTICS) }
            )
            AppScreen.DIAGNOSTICS -> DiagnosticsScreen(vm = vm, onBack = onBack)
        }
    }
}

private enum class AppScreen(val depth: Int, val parent: AppScreen?) {
    HOME(0, null),
    SETTINGS(1, HOME),
    DIAGNOSTICS(2, SETTINGS)
}
