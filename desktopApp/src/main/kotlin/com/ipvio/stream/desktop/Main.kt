package com.ipvio.stream.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ipvio.stream.shared.ui.SharedApp

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Korp TV"
    ) {
        SharedApp()
    }
}
