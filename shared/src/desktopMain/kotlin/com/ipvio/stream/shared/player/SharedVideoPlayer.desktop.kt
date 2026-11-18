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
import com.ipvio.stream.shared.platform.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JPanel

@Composable
actual fun SharedVideoPlayer(
    url: String,
    title: String,
    isLive: Boolean,
    modifier: Modifier,
    onBack: () -> Unit
) {
    // Tecla ESC global ou botão Voltar fecha o player e para a reprodução
    BackHandler(onBack = onBack)

    // Configura caminho explícito das bibliotecas LibVLC de 64 bits para o JNA
    remember {
        val vlcPath = "C:\\Program Files\\VideoLAN\\VLC"
        if (File(vlcPath).exists()) {
            val cur = System.getProperty("jna.library.path")
            if (cur == null || !cur.contains(vlcPath)) {
                System.setProperty("jna.library.path", if (cur.isNullOrBlank()) vlcPath else "$vlcPath;$cur")
            }
        }
        NativeDiscovery().discover()
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showBottomControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    // Componente nativo acelerado por hardware via LibVLC
    val mediaPlayerComponent = remember {
        EmbeddedMediaPlayerComponent()
    }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }

    // Registra listener de eventos do VLC
    DisposableEffect(mediaPlayer) {
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

            override fun error(mp: MediaPlayer?) {
                isBuffering = false
            }

            override fun finished(mp: MediaPlayer?) {
                isPlaying = false
            }
        }

        mediaPlayer.events().addMediaPlayerEventListener(listener)

        onDispose {
            try {
                mediaPlayer.events().removeMediaPlayerEventListener(listener)
                mediaPlayer.controls().stop()
            } catch (_: Throwable) {}
        }
    }

    // Inicia a reprodução SOMENTE quando o componente de vídeo estiver 100% montado e displayable
    LaunchedEffect(url) {
        isBuffering = true
        var attempts = 0
        while (!mediaPlayerComponent.videoSurfaceComponent().isDisplayable && attempts < 50) {
            delay(50)
            attempts++
        }
        delay(100)
        try {
            mediaPlayer.media().play(
                url,
                ":network-caching=3000",
                ":live-caching=3000",
                ":clock-jitter=0",
                ":clock-synchro=0",
                ":no-video-title-show"
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            isBuffering = false
        }
    }

    // Auto-ocultar barra inferior após 6 segundos
    LaunchedEffect(showBottomControls, isPlaying) {
        if (showBottomControls && isPlaying) {
            delay(6000)
            showBottomControls = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Barra Superior Fixa: SEMPRE VISÍVEL para garantir que o usuário nunca fique preso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF070B14))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botão Voltar destacado
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onBack() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("←", color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Voltar (ESC)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (isLive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33EF4444))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
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

        // 2. Área Central do Vídeo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            // Superfície Nativa do VLC (Acelerada por GPU)
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    JPanel().apply {
                        layout = BorderLayout()
                        add(mediaPlayerComponent, BorderLayout.CENTER)
                        mediaPlayerComponent.videoSurfaceComponent().addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                showBottomControls = !showBottomControls
                            }
                        })
                    }
                }
            )

            // Indicador de Carregamento
            if (isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            strokeWidth = 4.dp
                        )
                        Text("Conectando ao canal...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // 3. Barra Inferior de Controles (Play/Pause, Slider, Pular 10s)
        AnimatedVisibility(
            visible = showBottomControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color(0xFF0B101D))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceAround
                ) {
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
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isLive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        val t = (currentTimeMs - 10000).coerceAtLeast(0)
                                        mediaPlayer.controls().setTime(t)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⏮ 10s", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(Modifier.width(16.dp))
                        }

                        // Botão Play/Pause
                        Box(
                            modifier = Modifier
                                .size(46.dp)
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
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isLive) {
                            Spacer(Modifier.width(16.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        val t = (currentTimeMs + 10000).coerceAtMost(totalDurationMs)
                                        mediaPlayer.controls().setTime(t)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("10s ⏭", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
