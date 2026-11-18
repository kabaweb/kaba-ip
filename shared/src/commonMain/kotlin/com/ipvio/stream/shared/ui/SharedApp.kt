package com.ipvio.stream.shared.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ipvio.stream.shared.device.SharedDeviceIdentity
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

    val billboardItem = remember(all.size) { movies.firstOrNull { !it.logo.isNullOrBlank() } ?: all.firstOrNull() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // 1. Billboard Banner de Destaque
        if (billboardItem != null) {
            item {
                BillboardHeader(billboardItem)
            }
        }

        // 2. Fileira: Canais em Destaque
        if (live.isNotEmpty()) {
            item {
                SectionRow(title = "Canais Ao Vivo em Destaque", items = live.take(25), isLive = true)
            }
        }

        // 3. Fileira: Filmes Populares
        if (movies.isNotEmpty()) {
            item {
                SectionRow(title = "Filmes Adicionados Recentemente", items = movies.take(25), isLive = false)
            }
        }

        // 4. Fileira: Séries
        if (series.isNotEmpty()) {
            item {
                SectionRow(title = "Séries em Destaque", items = series.take(25), isLive = false)
            }
        }
    }
}

@Composable
fun BillboardHeader(item: Item) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        // Imagem de fundo com gradiente de fusão
        if (!item.logo.isNullOrBlank()) {
            AsyncImage(
                model = item.logo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            KorpLook.Bg.copy(alpha = 0.6f),
                            KorpLook.Bg
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            KorpLook.Bg.copy(alpha = 0.95f),
                            KorpLook.Bg.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Conteúdo do Banner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, bottom = 24.dp)
                .width(550.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(KorpLook.Accent1)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("EM DESTAQUE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Text(
                item.title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2
            )
            Text(
                "Categoria: ${item.group} ${if (item.year != null) "• " + item.year else ""}",
                color = KorpLook.TextMid,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SectionRow(title: String, items: List<Item>, isLive: Boolean) {
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
                    onClick = { /* Próximo passo: tocar vídeo */ },
                    cardWidth = if (isLive) 190.dp else 145.dp
                )
            }
        }
    }
}

/** Tela de TV Ao Vivo: 2 colunas estilo Android (Categorias na esquerda, canais com logo na direita) */
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
        // Logo do canal
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

/** Tela de Filmes ou Séries: Grade de Pôsteres verticais */
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

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, color = KorpLook.TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${filtered.size} títulos disponíveis", color = KorpLook.TextLow, fontSize = 13.sp)
            }
        }

        // Pílulas de filtro de categorias
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups.take(30)) { g ->
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

        // Carrossel em grade dos filmes/séries
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(filtered.take(60), key = { it.id }) { item ->
                SharedPosterCard(item = item, onClick = { /* Abrir detalhes */ }, cardWidth = 160.dp)
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
