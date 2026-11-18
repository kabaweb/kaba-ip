package com.ipvio.stream.shared.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun SharedVideoPlayer(
    url: String,
    title: String,
    isLive: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
)
