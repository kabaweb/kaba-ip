package com.ipvio.stream.shared.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun SharedVideoPlayer(
    url: String,
    title: String,
    isLive: Boolean,
    modifier: Modifier,
    onBack: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("Player Android integrado ao ExoPlayer", color = Color.White)
    }
}
