package com.ipvio.stream.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.BorderLayout
import java.awt.Canvas
import javax.swing.JPanel

@Composable
actual fun SharedVideoPlayer(
    url: String,
    title: String,
    isLive: Boolean,
    modifier: Modifier,
    onBack: () -> Unit
) {
    // Inicialização da descoberta de LibVLC no sistema
    remember {
        NativeDiscovery().discover()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    val mediaPlayerComponent = remember {
        EmbeddedMediaPlayerComponent()
    }

    val mediaPlayer = mediaPlayerComponent.mediaPlayer()

    // Configuração dos eventos do VLC
    DisposableEffect(url) {
        val listener = object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer?) {
                isPlaying = true
                isBuffering = false
            }

            override fun paused(mp: MediaPlayer?) {
                isPlaying = false
            }

            override fun timeChanged(mp: MediaPlayer?, newTime: Long) {
                currentTimeMs = newTime
            }

            override fun lengthChanged(mp: MediaPlayer?, newLength: Long) {
                totalDurationMs = newLength
            }

            override fun buffering(mp: MediaPlayer?, newCache: Float) {
                isBuffering = newCache < 100f
            }

            override fun finished(mp: MediaPlayer?) {
                isPlaying = false
            }
        }

        mediaPlayer.events().addMediaPlayerEventListener(listener)
        mediaPlayer.media().play(url)

        onDispose {
            mediaPlayer.events().removeMediaPlayerEventListener(listener)
            mediaPlayer.controls().stop()
            mediaPlayerComponent.release()
        }
    }

    // Auto-ocultar controles após 5 segundos
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // Camada Swing do VLC Player
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                JPanel().apply {
                    layout = BorderLayout()
                    add(mediaPlayerComponent, BorderLayout.CENTER)
                }
            }
        )

        // Indicador de Carregamento / Buffering
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 4.dp
                )
            }
        }

        // Overlay de Controles (Fade In/Out estilo Android TV)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Gradiente Superior para o Título
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.85f),
                                1f to Color.Transparent
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Botão Voltar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isLive) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Text(
                                        "AO VIVO",
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Gradiente Inferior para Barra de Progresso e Botões
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.9f)
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Linha de Progresso (apenas para filmes e séries)
                        if (!isLive && totalDurationMs > 0) {
                            val progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            Slider(
                                value = progress,
                                onValueChange = { newProg ->
                                    val newTime = (newProg * totalDurationMs).toLong()
                                    mediaPlayer.controls().setTime(newTime)
                                    currentTimeMs = newTime
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatTime(currentTimeMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text(formatTime(totalDurationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }

                        // Botões de Play/Pause / Rewind / Fast Forward
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isLive) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val t = (currentTimeMs - 10000).coerceAtLeast(0)
                                            mediaPlayer.controls().setTime(t)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("⏮ 10s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(Modifier.width(20.dp))
                            }

                            // Botão Play / Pause
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                                    .clickable {
                                        if (isPlaying) {
                                            mediaPlayer.controls().pause()
                                            isPlaying = false
                                        } else {
                                            mediaPlayer.controls().play()
                                            isPlaying = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isPlaying) "⏸" else "▶",
                                    color = Color.Black,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!isLive) {
                                Spacer(Modifier.width(20.dp))

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val t = (currentTimeMs + 10000).coerceAtMost(totalDurationMs)
                                            mediaPlayer.controls().setTime(t)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("10s ⏭", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
