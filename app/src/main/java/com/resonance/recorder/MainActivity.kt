package com.resonance.recorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.resonance.recorder.ui.splash.StartupSplashScreen
import kotlinx.coroutines.delay
import com.resonance.recorder.ui.MainScreen
import com.resonance.recorder.ui.MainViewModel
import com.resonance.recorder.ui.theme.ResonanceTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResonanceTheme {
                var introComplete by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!introComplete) {
                        delay(2_600L)
                        introComplete = true
                    }
                }
                Crossfade(targetState = introComplete,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                    label = "Startup handoff") { complete ->
                    if (complete) MainScreen(viewModel) else StartupSplashScreen()
                }
            }
        }
    }
}
