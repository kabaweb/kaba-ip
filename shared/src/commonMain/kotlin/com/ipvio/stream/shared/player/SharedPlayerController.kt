package com.ipvio.stream.shared.player

object SharedPlayerController {
    var onPlayPause: (() -> Unit)? = null
    var onRewind: (() -> Unit)? = null
    var onForward: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null

    fun playPause() = onPlayPause?.invoke()
    fun rewind() = onRewind?.invoke()
    fun forward() = onForward?.invoke()
    fun stop() = onStop?.invoke()
}
