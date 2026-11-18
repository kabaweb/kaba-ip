package com.ipvio.stream.shared.ui

import androidx.compose.animation.Crossfade
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
import com.ipvio.stream.shared.portal.SharedPortalManager

@Composable
fun SharedApp() {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        SharedPortalManager.startPolling(scope)
    }

    val hasPlaylist = SharedPortalManager.playlistItems.isNotEmpty()

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
                    0 -> SharedHomeScreen()
                    3 -> SharedLiveScreen()
                    1 -> SharedVodScreen("Filmes (VOD)", Kind.MOVIE)
                    2 -> SharedVodScreen("Séries", Kind.SERIES)
                    5 -> SharedSettingsView()
                    else -> SharedHomeScreen()
                }
            }
        }
    }
}

/** Tela Inicial estilo Android TV: Destaque Banner no topo + Carrosséis horizontais */
@Composable
fun SharedHomeScreen() {
    val all = SharedPortalManager.playlistItems
    val movies = remember(all.size) { all.filter { it.kind == Kind.MOVIE } }
    val series = remember(all.size) { all.filter { it.kind == Kind.SERIES } }
    val live = remember(all.size) { all.filter { it.kind == Kind.LIVE } }

    var focusedItem by remember { mutableStateOf(movies.firstOrNull() ?: all.firstOrNull()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Billboard Banner interativo que reage ao filme focado
        item {
            focusedItem?.let { BillboardHeader(it) }
        }

        // 2. Fileira: Filmes
        if (movies.isNotEmpty()) {
            item {
                SectionRow(
                    title = "Filmes Adicionados Recentemente",
                    items = movies.take(25),
                    isLive = false,
                    onHoverItem = { focusedItem = it }
                )
            }
        }

        // 3. Fileira: Séries
        if (series.isNotEmpty()) {
            item {
                SectionRow(
                    title = "Séries em Destaque",
                    items = series.take(25),
                    isLive = false,
                    onHoverItem = { focusedItem = it }
                )
            }
        }

        // 4. Fileira: Canais Ao Vivo
        if (live.isNotEmpty()) {
            item {
                SectionRow(
                    title = "Canais Ao Vivo em Destaque",
                    items = live.take(25),
                    isLive = true,
                    onHoverItem = { focusedItem = it }
                )
            }
        }
    }
}

/** Billboard Hero Banner oficial do Android com sinopse, nota e gêneros */
@Composable
fun BillboardHeader(item: Item) {
    var meta by remember(item.id) { mutableStateOf<SharedMeta?>(null) }

    LaunchedEffect(item.id) {
        meta = SharedMetaRepo.getMeta(item)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // Fundo com backdrop do filme
        Crossfade(targetState = meta?.backdrop ?: item.logo, label = "bbBackdrop") { bgUrl ->
            if (!bgUrl.isNullOrBlank()) {
                AsyncImage(
                    model = bgUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd
                )
            }
        }

        // Gradientes idênticos ao Android para legibilidade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to KorpLook.Bg.copy(alpha = 0.3f),
                        0.4f to Color.Transparent,
                        0.75f to KorpLook.Bg.copy(alpha = 0.85f),
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
                        0.35f to KorpLook.Bg.copy(alpha = 0.94f),
                        0.65f to KorpLook.Bg.copy(alpha = 0.4f),
                        1f to Color.Transparent
                    )
                )
        )

        // Informações completas do filme
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 20.dp)
                .width(620.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Badge Kicker
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            // Título
            Text(
                text = meta?.title ?: item.title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Linha de Metadados: Ano | Categoria | Nota
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val year = meta?.year ?: item.year?.toString()
                if (!year.isNullOrBlank()) {
                    Text(year, color = Color(0xFFE2E8F0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.width(1.dp).height(12.dp).background(Color(0x66FFFFFF)))
                }
                Text(item.group, color = Color(0xFFCBD5E1), fontSize = 14.sp)

                meta?.rating?.let { r ->
                    Box(Modifier.width(1.dp).height(12.dp).background(Color(0x66FFFFFF)))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("★", color = Color(0xFFFBBF24), fontSize = 14.sp)
                        Text(String.format("%.1f", r), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sinopse
            Text(
                text = meta?.overview ?: "Carregando informações...",
                color = KorpLook.TextMid,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionRow(
    title: String,
    items: List<Item>,
    isLive: Boolean,
    onHoverItem: (Item) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = KorpLook.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            items(items, key = { it.id }) { item ->
                SharedPosterCard(
                    item = item,
                    onClick = { onHoverItem(item) },
                    cardWidth = if (isLive) 190.dp else 145.dp
                )
            }
        }
    }
}

/** Tela de TV Ao Vivo: 2 colunas estilo Android */
@Composable
fun SharedLiveScreen() {
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
        // Coluna 1: Categorias de Canais
        Column(
            modifier = Modifier
                .width(240.dp)
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

        // Coluna 2: Grade de Canais com Logos
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
                    LiveChannelRow(ch)
                }
            }
        }
    }
}

@Composable
fun LiveChannelRow(channel: Item) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(KorpLook.Surf)
            .clickable { /* Abrir player */ }
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

/** Tela de Filmes ou Séries: com Banner no topo exibindo informações do filme focado */
@Composable
fun SharedVodScreen(title: String, kind: Kind) {
    val items = remember(kind, SharedPortalManager.playlistItems.size) {
        SharedPortalManager.playlistItems.filter { it.kind == kind }
    }
    val groups = remember(items) {
        listOf("Todos") + items.map { it.group }.distinct()
    }
    var selectedGroup by remember { mutableStateOf("Todos") }

    val filtered = remember(selectedGroup, items) {
        if (selectedGroup == "Todos") items else items.filter { it.group == selectedGroup }
    }

    var focusedVod by remember(kind, filtered) { mutableStateOf(filtered.firstOrNull()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Banner do filme/série com detalhes no topo
        item {
            focusedVod?.let { BillboardHeader(it) }
        }

        // Pílulas de filtro de categorias
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = KorpLook.TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${filtered.size} títulos disponíveis", color = KorpLook.TextLow, fontSize = 13.sp)
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups.take(40)) { g ->
                        val active = selectedGroup == g
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) KorpLook.Accent1 else KorpLook.Surf)
                                .clickable { selectedGroup = g }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                g,
                                color = if (active) Color.Black else KorpLook.TextMid,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Carrossel de pôsteres com efeito de seleção
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filtered.take(60), key = { it.id }) { item ->
                        SharedPosterCard(
                            item = item,
                            onClick = { focusedVod = item },
                            cardWidth = 155.dp
                        )
                    }
                }
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
