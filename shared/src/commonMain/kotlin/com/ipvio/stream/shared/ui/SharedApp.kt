package com.ipvio.stream.shared.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ipvio.stream.shared.device.SharedDeviceIdentity
import com.ipvio.stream.shared.meta.SharedMeta
import com.ipvio.stream.shared.meta.SharedMetaRepo
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import com.ipvio.stream.shared.player.SharedVideoPlayer
import com.ipvio.stream.shared.portal.SharedPortalManager

@Composable
fun SharedApp() {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var activePlayingItem by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Unit) {
        SharedPortalManager.startPolling(scope)
    }

    val hasPlaylist = SharedPortalManager.playlistItems.isNotEmpty()

    // Se estiver reproduzindo vídeo, exibe o player em tela inteira
    if (activePlayingItem != null) {
        val playItem = activePlayingItem!!
        SharedVideoPlayer(
            url = playItem.url,
            title = playItem.title,
            isLive = playItem.kind == Kind.LIVE,
            modifier = Modifier.fillMaxSize(),
            onBack = { activePlayingItem = null }
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize().background(KorpLook.Bg)) {
        SharedSidebar(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KorpLook.Bg)
        ) {
            if (!hasPlaylist && !SharedPortalManager.isLoadingPlaylist) {
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    PairingScreen()
                }
            } else if (SharedPortalManager.isLoadingPlaylist) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = KorpLook.Accent1)
                    Spacer(Modifier.height(16.dp))
                    Text(SharedPortalManager.statusMessage, color = KorpLook.TextHi, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                when (selectedTab) {
                    0 -> AndroidHomeScreen(Kind.MOVIE, onPlay = { activePlayingItem = it })
                    1 -> AndroidHomeScreen(Kind.MOVIE, onPlay = { activePlayingItem = it })
                    2 -> AndroidHomeScreen(Kind.SERIES, onPlay = { activePlayingItem = it })
                    3 -> SharedLiveScreen(onPlay = { activePlayingItem = it })
                    5 -> SharedSettingsView()
                    else -> AndroidHomeScreen(Kind.MOVIE, onPlay = { activePlayingItem = it })
                }
            }
        }
    }
}

/**
 * Interface Oficial HomeScreen e VOD Screen do Android TV:
 * Banner Billboard imersivo com backdrop dinâmico + Sinopse + Ano/Nota + Carrosséis agrupados por Categorias
 */
@Composable
fun AndroidHomeScreen(targetKind: Kind, onPlay: (Item) -> Unit) {
    val all = SharedPortalManager.playlistItems
    val items = remember(all.size, targetKind) { all.filter { it.kind == targetKind } }

    val groupsMap = remember(items) {
        items.groupBy { it.group }
    }
    val groupNames = remember(groupsMap) {
        groupsMap.keys.toList()
    }

    var focusedItem by remember(items) { mutableStateOf(items.firstOrNull()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "billboard") {
            focusedItem?.let {
                AndroidBillboardLayer(it, onPlay = { onPlay(it) })
            }
        }

        items(groupNames.take(20), key = { it }) { groupName ->
            val rowItems = groupsMap[groupName].orEmpty()
            if (rowItems.isNotEmpty()) {
                CategoryCarrouselRow(
                    categoryTitle = groupName,
                    items = rowItems,
                    focusedItem = focusedItem,
                    onSelect = { focusedItem = it },
                    onPlay = onPlay
                )
            }
        }
    }
}

/** Billboard Layer do Android TV com sinopse, nota, banner em alta resolução e botão de assistir */
@Composable
fun AndroidBillboardLayer(item: Item, onPlay: () -> Unit) {
    var meta by remember(item.id) { mutableStateOf<SharedMeta?>(null) }

    LaunchedEffect(item.id) {
        meta = SharedMetaRepo.getMeta(item)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(410.dp)
    ) {
        val bgUrl = meta?.backdrop ?: item.logo
        Crossfade(targetState = bgUrl, animationSpec = tween(400), label = "bbBackdrop") { url ->
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to KorpLook.Bg.copy(alpha = 0.4f),
                        0.35f to Color.Transparent,
                        0.7f to KorpLook.Bg.copy(alpha = 0.85f),
                        1f to KorpLook.Bg
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to KorpLook.Bg,
                        0.3f to KorpLook.Bg.copy(alpha = 0.95f),
                        0.65f to KorpLook.Bg.copy(alpha = 0.5f),
                        1f to Color.Transparent
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 18.dp)
                .width(660.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(KorpLook.Accent1)
                )
                Text(
                    text = when (item.kind) {
                        Kind.LIVE -> "CANAL AO VIVO"
                        Kind.MOVIE -> "FILME"
                        Kind.SERIES -> "SÉRIE"
                    } + (if (!item.quality.isNullOrBlank()) "  ·  ${item.quality}" else ""),
                    color = KorpLook.Accent1,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Text(
                text = meta?.title ?: item.title,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val year = meta?.year ?: item.year?.toString()
                if (!year.isNullOrBlank()) {
                    Text(year, color = Color(0xFFE2E8F0), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.width(1.dp).height(14.dp).background(Color(0x66FFFFFF)))
                }
                Text(item.group, color = Color(0xFFCBD5E1), fontSize = 14.sp, fontWeight = FontWeight.Medium)

                meta?.rating?.let { r ->
                    Box(Modifier.width(1.dp).height(14.dp).background(Color(0x66FFFFFF)))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("★", color = Color(0xFFFBBF24), fontSize = 15.sp)
                        Text(String.format("%.1f", r), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = meta?.overview ?: "Carregando sinopse e informações...",
                color = KorpLook.TextMid,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Botão Assistir Agora
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(KorpLook.Accent1)
                    .clickable { onPlay() }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("▶", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Assistir Agora", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Fileira horizontal de uma categoria com cartões no padrão Android TV */
@Composable
fun CategoryCarrouselRow(
    categoryTitle: String,
    items: List<Item>,
    focusedItem: Item?,
    onSelect: (Item) -> Unit,
    onPlay: (Item) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(categoryTitle, color = KorpLook.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${items.size} títulos", color = KorpLook.TextLow, fontSize = 12.sp)
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            items(items.take(40), key = { it.id }) { item ->
                SharedPosterCard(
                    item = item,
                    onClick = {
                        if (focusedItem?.id == item.id) {
                            onPlay(item)
                        } else {
                            onSelect(item)
                        }
                    },
                    cardWidth = 150.dp
                )
            }
        }
    }
}

/** Tela de TV Ao Vivo: 2 colunas estilo Android */
@Composable
fun SharedLiveScreen(onPlay: (Item) -> Unit) {
    val allLive = remember(SharedPortalManager.playlistItems.size) {
        SharedPortalManager.playlistItems.filter { it.kind == Kind.LIVE }
    }
    val groups = remember(allLive) {
        listOf("Todos") + allLive.map { it.group }.distinct()
    }
    var selectedGroup by remember { mutableStateOf("Todos") }

    val filteredLive = remember(selectedGroup, allLive) {
        if (selectedGroup == "Todos") allLive else allLive.filter { it.group == selectedGroup }
    }

    Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            modifier = Modifier
                .width(250.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(KorpLook.Surf)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Categorias", color = KorpLook.TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(groups) { g ->
                    val active = selectedGroup == g
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) KorpLook.Accent1 else Color.Transparent)
                            .clickable { selectedGroup = g }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            g,
                            color = if (active) Color.Black else KorpLook.TextMid,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedGroup, color = KorpLook.TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${filteredLive.size} canais", color = KorpLook.TextLow, fontSize = 13.sp)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLive, key = { it.id }) { ch ->
                    LiveChannelRow(ch, onClick = { onPlay(ch) })
                }
            }
        }
    }
}

@Composable
fun LiveChannelRow(channel: Item, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(KorpLook.Surf)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.title,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("TV", color = KorpLook.Accent1, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(channel.title, color = KorpLook.TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(channel.group, color = KorpLook.TextLow, fontSize = 11.sp, maxLines = 1)
        }

        if (!channel.quality.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x334ADE80))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(channel.quality, color = KorpLook.Accent1, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PairingScreen() {
    val pairingCode = remember { SharedDeviceIdentity.getPairingCode() }
    val virtualMac = remember { SharedDeviceIdentity.getVirtualMac() }
    val portalUrl = remember { SharedPortalManager.getPortalUrl() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0C1929))
                .border(1.5.dp, Color(0xFF06B6D4), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔗", fontSize = 20.sp)
                Text("Vincular pelo Portal Web", color = Color(0xFF38BDF8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Text("Acesse no computador ou celular: $portalUrl", color = Color(0xFFA1A1AA), fontSize = 13.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF07111D), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CÓDIGO DE ATIVAÇÃO", color = Color(0xFF67E8F9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(pairingCode, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("MAC VIRTUAL", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                    Text(virtualMac, color = Color(0xFFE4E4E7), fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Text(
                "Basta digitar este código no painel para carregar sua lista automaticamente nesta tela.",
                color = Color(0xFF34D399),
                fontSize = 13.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Status: ${SharedPortalManager.statusMessage}",
                    color = if (SharedPortalManager.isConnected) Color(0xFF10B981) else Color(0xFFF87171),
                    fontSize = 12.sp
                )
                if (SharedPortalManager.isConnected) {
                    Text("🟢 Conectado", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("🔴 Desconectado", color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SharedSettingsView() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Configurações do Korp TV", color = KorpLook.TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Gerencie os servidores e conexões do app.", color = KorpLook.TextLow, fontSize = 14.sp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KorpLook.Surf)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Conexão com o Portal Remoto", color = KorpLook.TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("URL Atual: ${SharedPortalManager.getPortalUrl()}", color = KorpLook.TextMid, fontSize = 13.sp)
                Text("Código de Ativação deste PC: ${SharedDeviceIdentity.getPairingCode()}", color = Color(0xFF67E8F9), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("MAC Virtual deste PC: ${SharedDeviceIdentity.getVirtualMac()}", color = KorpLook.TextLow, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
