package com.ipvio.stream.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

object BackDispatcher {
    private val handlers = mutableListOf<() -> Boolean>()

    fun register(handler: () -> Boolean) {
        handlers.add(handler)
    }

    fun unregister(handler: () -> Boolean) {
        handlers.remove(handler)
    }

    fun onBackPressed(): Boolean {
        for (i in handlers.indices.reversed()) {
            if (handlers[i].invoke()) return true
        }
        return false
    }
}

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    DisposableEffect(enabled, onBack) {
        if (!enabled) return@DisposableEffect onDispose {}
        val handler = {
            onBack()
            true
        }
        BackDispatcher.register(handler)
        onDispose {
            BackDispatcher.unregister(handler)
        }
    }
}
