package com.alpwarestudio.chargefreeze

import android.content.Context
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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
                AppNavigation(
                    vm = vm,
                    preferences = preferences,
                    themeMode = themeMode,
                    onThemeChanged = { mode ->
                        preferences.themeMode = mode
                        themeMode = mode
                    },
                    onLanguageChanged = {
                        preferences.languageMode = it
                        recreate()
                    }
                )
            }
        }
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
    var backInProgress by remember { mutableStateOf(false) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var skipNextTransition by remember { mutableStateOf(false) }

    fun navigate(destination: AppScreen) {
        screenName = destination.name
    }

    fun navigateBack() {
        screen.parent?.let { screenName = it.name }
    }

    PredictiveBackHandler(enabled = screen.parent != null) { events ->
        try {
            backInProgress = true
            events.collect { event ->
                backEdge = event.swipeEdge
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            skipNextTransition = true
            screen.parent?.let { screenName = it.name }
            backProgress.snapTo(0f)
            backInProgress = false
        } catch (cancelled: CancellationException) {
            backProgress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
            backInProgress = false
        }
    }

    LaunchedEffect(screenName) {
        if (skipNextTransition) skipNextTransition = false
    }

    Box(Modifier.fillMaxSize()) {
        if (backInProgress) {
            screen.parent?.let { destination ->
                AppScreenContent(
                    screen = destination,
                    vm = vm,
                    preferences = preferences,
                    themeMode = themeMode,
                    onThemeChanged = onThemeChanged,
                    onLanguageChanged = onLanguageChanged,
                    onNavigate = ::navigate,
                    onBack = ::navigateBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = backProgress.value
                            val scale = 0.94f + (0.06f * progress)
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.72f + (0.28f * progress)
                        }
                )
            }
        }

        AnimatedContent(
            targetState = screen,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = backProgress.value
                    val direction = if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                    translationX = size.width * 0.12f * progress * direction
                    val scale = 1f - (0.08f * progress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(
                        pivotFractionX = if (direction > 0) 1f else 0f,
                        pivotFractionY = 0.5f
                    )
                    shape = RoundedCornerShape((28f * progress).dp)
                    clip = progress > 0f
                    shadowElevation = 10.dp.toPx() * progress
                },
            transitionSpec = {
                navigationTransition(
                    movingForward = targetState.depth > initialState.depth,
                    skipAnimation = skipNextTransition
                )
            },
            label = "app-screen"
        ) { destination ->
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

private fun navigationTransition(
    movingForward: Boolean,
    skipAnimation: Boolean
): ContentTransform {
    if (skipAnimation) return EnterTransition.None togetherWith ExitTransition.None

    val duration = 300
    val enter = slideInHorizontally(
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        initialOffsetX = { width -> if (movingForward) width / 5 else -width / 6 }
    ) + fadeIn(tween(durationMillis = 180, delayMillis = 45))
    val exit = slideOutHorizontally(
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        targetOffsetX = { width -> if (movingForward) -width / 8 else width / 5 }
    ) + fadeOut(tween(durationMillis = 150))
    return enter togetherWith exit
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
