package com.nova.galaxysandbox

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.nova.galaxysandbox.audio.Sfx
import com.nova.galaxysandbox.engine.GameEngine
import com.nova.galaxysandbox.engine.GameMode
import com.nova.galaxysandbox.engine.HudSnapshot
import com.nova.galaxysandbox.ui.GameHud
import com.nova.galaxysandbox.ui.HelpSheet
import com.nova.galaxysandbox.ui.MainMenu
import com.nova.galaxysandbox.ui.Palette
import com.nova.galaxysandbox.view.GameSurfaceView
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val engine = GameEngine()
    private var sfx: Sfx? = null
    private var surface: GameSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sfx = Sfx()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Palette.Cyan,
                    secondary = Palette.Violet,
                    background = Palette.Space,
                    surface = Palette.Space,
                    onPrimary = Color.Black,
                    onBackground = Palette.TextHi,
                    onSurface = Palette.TextHi
                )
            ) {
                GameRoot()
            }
        }
    }

    @Composable
    private fun GameRoot() {
        var started by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        var hud by remember { mutableStateOf(HudSnapshot()) }
        var everStarted by remember { mutableStateOf(false) }

        // Poll the engine snapshot for the UI at 8 Hz — cheap and always current.
        LaunchedEffect(started) {
            while (started) {
                hud = engine.hud
                delay(120)
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (started) {
                AndroidView(
                    factory = { ctx ->
                        GameSurfaceView(ctx, engine, sfx).also { surface = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                GameHud(
                    engine = engine,
                    hud = hud,
                    onExit = { engine.exitPlanet() },
                    onMenu = { started = false }
                )
                BackHandler(enabled = true) {
                    if (engine.mode == GameMode.PLANET) engine.exitPlanet() else started = false
                }
            }

            AnimatedVisibility(visible = !started, enter = fadeIn(), exit = fadeOut()) {
                MainMenu(
                    onNewGalaxy = { size ->
                        engine.newGalaxy(System.currentTimeMillis(), size)
                        started = true
                        everStarted = true
                    },
                    onContinue = if (everStarted) ({ started = true }) else null,
                    onHelp = { showHelp = true }
                )
            }

            if (showHelp) {
                HelpSheet(onClose = { showHelp = false })
            }
        }

        DisposableEffect(started) {
            onDispose { if (!started) surface?.stop() }
        }
    }

    private fun goFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    override fun onPause() {
        super.onPause()
        surface?.stop()
    }

    override fun onResume() {
        super.onResume()
        surface?.start()
    }
}
