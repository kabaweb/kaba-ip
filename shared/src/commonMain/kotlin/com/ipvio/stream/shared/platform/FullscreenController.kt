package com.ipvio.stream.shared.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object FullscreenController {
    var isFullscreen by mutableStateOf(false)
    var onToggle: (() -> Unit)? = null

    fun toggle() {
        onToggle?.invoke()
    }
}
