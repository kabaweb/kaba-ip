package com.ipvio.stream.shared.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ipvio.stream.shared.epg.SharedEpgEntry
import com.ipvio.stream.shared.epg.SharedEpgRepo
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import com.ipvio.stream.shared.platform.BackHandler
import com.ipvio.stream.shared.platform.FullscreenController
import com.ipvio.stream.shared.portal.SharedPortalManager
import com.ipvio.stream.shared.ui.KorpLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // Configura caminho do LibVLC
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

    var currentPlayingUrl by remember(url) { mutableStateOf(url) }
    var currentPlayingTitle by remember(title) { mutableStateOf(title) }

    // Objeto do canal atual para EPG e metadados
    val currentChannelItem = remember(currentPlayingUrl) {
        SharedPortalManager.playlistItems.firstOrNull { it.url == currentPlayingUrl }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var showChannelDrawer by remember { mutableStateOf(false) }
    var showEpgBanner by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    // EPG do canal atual
    var epgList by remember { mutableStateOf<List<SharedEpgEntry>>(emptyList()) }
    val currentEpg = remember(epgList) { SharedEpgRepo.current(epgList) }
    val nextEpg = remember(epgList) { SharedEpgRepo.next(epgList) }

    // Carrega EPG do canal atual em segundo plano
    LaunchedEffect(currentPlayingUrl) {
        showEpgBanner = true
        val ch = currentChannelItem
        if (ch != null && isLive) {
            epgList = SharedEpgRepo.getEpg(ch)
        } else {
            epgList = emptyList()
        }
    }

    // Componente nativo de vídeo acelerado por hardware
    val mediaPlayerComponent = remember { EmbeddedMediaPlayerComponent() }
    val mediaPlayer = remember { mediaPlayerComponent.mediaPlayer() }

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

    // Inicia a reprodução
    LaunchedEffect(currentPlayingUrl) {
        isBuffering = true
        var attempts = 0
        while (!mediaPlayerComponent.videoSurfaceComponent().isDisplayable && attempts < 50) {
            delay(50)
            attempts++
        }
        delay(100)
        try {
            mediaPlayer.media().play(
                currentPlayingUrl,
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

    // Auto-ocultar controles após 5 segundos de inatividade
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
            showEpgBanner = false
        }
    }

    val allLiveChannels = remember {
        SharedPortalManager.playlistItems.filter { it.kind == Kind.LIVE }
    }

    // Layout com VÍDEO EM TELA TODA (100% da área do Box) e Controles como Overlays Flutuantes
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Camada de Vídeo Acelerada por GPU (OCUPA 100% DA JANELA SEM BARRAS PRETAS)
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                JPanel().apply {
                    layout = BorderLayout()
                    add(mediaPlayerComponent, BorderLayout.CENTER)

                    val surface = mediaPlayerComponent.videoSurfaceComponent()
                    surface.addMouseMotionListener(object : MouseMotionAdapter() {
                        override fun mouseMoved(e: MouseEvent?) {
                            showControls = true
                        }
                    })
                    surface.addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.clickCount == 2) {
                                FullscreenController.toggle()
                            } else {
                                showControls = !showControls
                                if (showControls && isLive) showEpgBanner = true
                            }
                        }
                    })
                }
            }
        )

        // 2. Indicador de Buffering / Carregamento
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xD90A0E1A))
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                ) {
                    CircularProgressIndicator(
                        color = KorpLook.Accent1,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Text("Carregando transmissão...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 3. Barra Superior Flutuante (Gradiente Superior)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xF0070B14), Color(0xCC070B14), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Botão Voltar e Título
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .clickable { onBack() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("←", color = KorpLook.Accent1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Voltar (ESC)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Column {
                            Text(
                                text = currentPlayingTitle,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isLive) {
                                Text(
                                    if (currentEpg != null) "No ar: ${currentEpg.title}" else "Transmissão Ao Vivo",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Ações da Direita (Zapping, Ao Vivo e Tela Cheia)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isLive) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (showChannelDrawer) KorpLook.Accent1 else Color(0xFF1E293B))
                                    .clickable { showChannelDrawer = !showChannelDrawer }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("≡", color = if (showChannelDrawer) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Canais (Zapping)", color = if (showChannelDrawer) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x33EF4444))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Botão de Tela Cheia Oficial
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .clickable { FullscreenController.toggle() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(if (FullscreenController.isFullscreen) "🗗" else "⛶", color = KorpLook.Accent1, fontSize = 14.sp)
                            Text(
                                if (FullscreenController.isFullscreen) "Janela" else "Tela Cheia (F11)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 4. Banner de EPG e Informações do Canal (Idêntico ao Android TV)
        AnimatedVisibility(
            visible = isLive && (showControls || showEpgBanner),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000), Color(0xF2070B14))
                        )
                    )
                    .padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Logo do Canal
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        val logo = currentChannelItem?.logo
                        if (!logo.isNullOrBlank()) {
                            AsyncImage(
                                model = logo,
                                contentDescription = currentPlayingTitle,
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = currentPlayingTitle.take(2).uppercase(),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Informações do Programa Atual e Barra de Progresso
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE5484D))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            Text(
                                text = currentPlayingTitle,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            currentChannelItem?.quality?.takeIf { it.isNotBlank() }?.let { q ->
                                Text(
                                    text = q,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x40FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (currentEpg != null) {
                            val nowSec = System.currentTimeMillis() / 1000
                            val progFrac = ((nowSec - currentEpg.start).toFloat() / (currentEpg.end - currentEpg.start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentEpg.title,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    "${formatTimestamp(currentEpg.start)} - ${formatTimestamp(currentEpg.end)}",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 14.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x40FFFFFF))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progFrac)
                                        .fillMaxHeight()
                                        .background(KorpLook.Accent1)
                                )
                            }
                        } else {
                            Text(
                                "Carregando programação do canal...",
                                color = Color(0xFFA1A1AA),
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Próximo Programa ("A Seguir")
                    if (nextEpg != null) {
                        Column(
                            modifier = Modifier.widthIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("A Seguir", color = KorpLook.Accent1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${formatTimestamp(nextEpg.start)}  ${nextEpg.title}",
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 5. Barra Inferior de Controles para VOD (Filmes / Séries)
        if (!isLive) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC0B101D), Color(0xF20B101D))
                            )
                        )
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (totalDurationMs > 0) {
                            val progress = (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(formatTime(currentTimeMs), color = Color.White, fontSize = 12.sp)
                                Slider(
                                    value = progress,
                                    onValueChange = { newProg ->
                                        val newTime = (newProg * totalDurationMs).toLong()
                                        mediaPlayer.controls().setTime(newTime)
                                        currentTimeMs = newTime
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = KorpLook.Accent1,
                                        activeTrackColor = KorpLook.Accent1,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Text(formatTime(totalDurationMs), color = Color.White, fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        val t = (currentTimeMs - 10000).coerceAtLeast(0)
                                        mediaPlayer.controls().setTime(t)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⏮ 10s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(Modifier.width(20.dp))

                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(KorpLook.Accent1)
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
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.width(20.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        val t = (currentTimeMs + 10000).coerceAtMost(totalDurationMs)
                                        mediaPlayer.controls().setTime(t)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("10s ⏭", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 6. Gaveta Lateral de Canais (Zapping rápido)
        if (isLive && showChannelDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xF50B101D))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lista de Canais", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "✕ Fechar",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { showChannelDrawer = false }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allLiveChannels) { ch ->
                            val isCurrent = ch.url == currentPlayingUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) KorpLook.Accent1 else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        currentPlayingUrl = ch.url
                                        currentPlayingTitle = ch.title
                                        showChannelDrawer = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isCurrent) {
                                    Text("▶", color = Color.Black, fontSize = 12.sp)
                                }
                                Text(
                                    ch.title,
                                    color = if (isCurrent) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(sec: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(sec * 1000L))
    } catch (_: Throwable) {
        ""
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
