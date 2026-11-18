package com.ipvio.stream.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import com.ipvio.stream.shared.platform.BackDispatcher
import com.ipvio.stream.shared.platform.FullscreenController
import com.ipvio.stream.shared.player.SharedPlayerController
import com.ipvio.stream.shared.ui.SharedApp

fun main() = application {
    // Interceptor global de teclado do AWT: atalhos funcionam mesmo com foco dentro do VLC/Swing
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
        if (event.id == java.awt.event.KeyEvent.KEY_RELEASED) {
            when (event.keyCode) {
                java.awt.event.KeyEvent.VK_ESCAPE -> {
                    BackDispatcher.onBackPressed()
                    true
                }
                java.awt.event.KeyEvent.VK_F11 -> {
                    FullscreenController.toggle()
                    true
                }
                java.awt.event.KeyEvent.VK_SPACE -> {
                    if (SharedPlayerController.onPlayPause != null) {
                        SharedPlayerController.playPause()
                        true
                    } else false
                }
                java.awt.event.KeyEvent.VK_LEFT -> {
                    if (SharedPlayerController.onRewind != null) {
                        SharedPlayerController.rewind()
                        true
                    } else false
                }
                java.awt.event.KeyEvent.VK_RIGHT -> {
                    if (SharedPlayerController.onForward != null) {
                        SharedPlayerController.forward()
                        true
                    } else false
                }
                java.awt.event.KeyEvent.VK_S -> {
                    if (SharedPlayerController.onStop != null) {
                        SharedPlayerController.stop()
                        true
                    } else false
                }
                else -> false
            }
        } else false
    }

    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))

    FullscreenController.onToggle = {
        val current = windowState.placement
        val next = if (current == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
        windowState.placement = next
        FullscreenController.isFullscreen = next == WindowPlacement.Fullscreen
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Korp TV",
        undecorated = false,
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp) {
                when (keyEvent.key) {
                    Key.Escape -> BackDispatcher.onBackPressed()
                    Key.F11, Key.F -> {
                        FullscreenController.toggle()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        SharedApp()
    }
}
