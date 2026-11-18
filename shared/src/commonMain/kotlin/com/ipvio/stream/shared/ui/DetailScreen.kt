package com.ipvio.stream.shared.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ipvio.stream.shared.meta.SharedMeta
import com.ipvio.stream.shared.meta.SharedMetaRepo
import com.ipvio.stream.shared.model.Episode
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import com.ipvio.stream.shared.platform.BackHandler

/**
 * Tela de Detalhes oficial inspirada no DetailScreen do Android TV:
 * Backdrop imersivo em tela cheia, Pôster, Nota TMDB, Sinopse,
 * Navegação com foco (Teclado/Controle Remoto), e suporte a Temporadas/Episódios para séries.
 */
@Composable
fun DetailScreen(
    item: Item,
    onPlay: (url: String, title: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var meta by remember(item.id) { mutableStateOf<SharedMeta?>(null) }
    var isLoadingMeta by remember(item.id) { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoadingMeta = true
        meta = SharedMetaRepo.getMeta(item)
        isLoadingMeta = false
    }

    // Gerenciamento de foco do botão inicial
    val playButtonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            playButtonFocus.requestFocus()
        } catch (_: Throwable) {}
    }

    // Temporadas e episódios se for série
    val isSeries = item.kind == Kind.SERIES
    val seasons = remember(item.episodes) {
        item.episodes.map { it.season }.distinct().sorted()
    }
    var selectedSeason by remember(seasons) {
        mutableStateOf(seasons.firstOrNull() ?: 1)
    }
    val currentEpisodes = remember(item.episodes, selectedSeason) {
        if (item.episodes.isEmpty()) emptyList()
        else item.episodes.filter { it.season == selectedSeason }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KorpLook.Bg)
    ) {
        // 1. Fundo Backdrop de Alta Resolução com Gradientes Cinematográficos
        val bgUrl = meta?.backdrop ?: item.logo
        Crossfade(targetState = bgUrl, animationSpec = tween(500), label = "detailBackdrop") { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd
                )
            }
        }

        // Camadas de degradê escuro para legibilidade idênticas ao Android TV
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to KorpLook.Bg.copy(alpha = 0.5f),
                        0.4f to KorpLook.Bg.copy(alpha = 0.75f),
                        0.85f to KorpLook.Bg,
                        1f to KorpLook.Bg
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to KorpLook.Bg.copy(alpha = 0.98f),
                        0.35f to KorpLook.Bg.copy(alpha = 0.9f),
                        0.7f to KorpLook.Bg.copy(alpha = 0.5f),
                        1f to Color.Transparent
                    )
                )
        )

        // 2. Conteúdo com Scroll Vertical
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Barra Superior: Botão Voltar
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FocusablePillButton(
                        text = "← Voltar",
                        icon = null,
                        onClick = onBack,
                        focusColor = KorpLook.Accent1
                    )
                    Text(
                        text = when (item.kind) {
                            Kind.LIVE -> "CANAL DE TV"
                            Kind.MOVIE -> "FILME"
                            Kind.SERIES -> "SÉRIE"
                        },
                        color = KorpLook.Accent1,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Bloco de Informações Principais: Pôster + Metadados + Sinopse + Botão Assistir
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Pôster Vertical
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(KorpLook.Surf2)
                            .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    ) {
                        val posterUrl = meta?.poster ?: item.logo
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Detalhes Textuais
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Título
                        Text(
                            text = meta?.title ?: item.title,
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 44.sp
                        )

                        // Tags: Ano, Nota TMDB, Qualidade, Categoria
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val year = meta?.year ?: item.year?.toString()
                            if (!year.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(year, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            meta?.rating?.let { r ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x33FBBF24))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("★", color = Color(0xFFFBBF24), fontSize = 14.sp)
                                    Text(String.format("%.1f", r), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!item.quality.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KorpLook.Accent1.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(item.quality, color = KorpLook.Accent1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(item.group, color = KorpLook.TextMid, fontSize = 13.sp)
                        }

                        // Sinopse
                        Text(
                            text = meta?.overview ?: "Carregando sinopse e detalhes...",
                            color = Color(0xFFD1D5DB),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        // Botões de Ação com Foco
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FocusablePillButton(
                                text = if (isSeries) "Assistir 1º Episódio" else "Assistir Agora",
                                icon = "▶",
                                onClick = {
                                    if (isSeries && currentEpisodes.isNotEmpty()) {
                                        val firstEp = currentEpisodes.first()
                                        onPlay(firstEp.url, "${item.title} - T${firstEp.season}E${firstEp.num}")
                                    } else {
                                        onPlay(item.url, item.title)
                                    }
                                },
                                focusRequester = playButtonFocus,
                                isPrimary = true
                            )
                        }
                    }
                }
            }

            // Se for Série: Abas de Temporadas e Lista de Episódios
            if (isSeries && seasons.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "Temporadas",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Carrossel de Temporadas
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(seasons) { s ->
                                val active = s == selectedSeason
                                FocusableSeasonChip(
                                    title = "Temporada $s",
                                    selected = active,
                                    onClick = { selectedSeason = s }
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Episódios (${currentEpisodes.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (currentEpisodes.isEmpty()) {
                            Text("Nenhum episódio cadastrado para esta temporada.", color = KorpLook.TextLow, fontSize = 13.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(currentEpisodes) { ep ->
                                    EpisodeCard(
                                        episode = ep,
                                        seriesTitle = item.title,
                                        onClick = {
                                            onPlay(ep.url, "${item.title} - T${ep.season} E${ep.num} ${ep.title}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusablePillButton(
    text: String,
    icon: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    focusColor: Color = KorpLook.Accent1,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "btnScale")

    var mod = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .onFocusChanged { isFocused = it.isFocused }
        .clickable(onClick = onClick)
        .focusable()

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    val bg = when {
        isFocused -> focusColor
        isPrimary -> focusColor.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    val textColor = when {
        isFocused || isPrimary -> Color.Black
        else -> Color.White
    }

    Row(
        modifier = mod
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!icon.isNullOrBlank()) {
            Text(icon, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FocusableSeasonChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) KorpLook.Accent1 else if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            title,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun EpisodeCard(
    episode: Episode,
    seriesTitle: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "epScale")

    Column(
        modifier = Modifier
            .width(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(KorpLook.Surf)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) KorpLook.Accent1 else KorpLook.Line,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(KorpLook.Accent1.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("EP ${episode.num}", color = KorpLook.Accent1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text("▶", color = if (isFocused) KorpLook.Accent1 else KorpLook.TextLow, fontSize = 12.sp)
        }

        Text(
            episode.title.ifBlank { "Episódio ${episode.num}" },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
