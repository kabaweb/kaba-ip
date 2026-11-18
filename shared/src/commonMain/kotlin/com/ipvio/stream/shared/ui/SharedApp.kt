package com.ipvio.stream.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .padding(24.dp)
        ) {
            if (!hasPlaylist && !SharedPortalManager.isLoadingPlaylist) {
                // Se ainda não tem playlist, exibe o cartão idêntico ao Android de Pareamento com o Portal
                PairingScreen()
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
                    0 -> SharedHomeView()
                    3 -> ChannelListView("Canais Ao Vivo", Kind.LIVE)
                    1 -> ChannelListView("Filmes (VOD)", Kind.MOVIE)
                    2 -> ChannelListView("Séries", Kind.SERIES)
                    5 -> SharedSettingsView()
                    else -> SharedHomeView()
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
fun ChannelListView(title: String, kind: Kind) {
    val items = remember(kind, SharedPortalManager.playlistItems.size) {
        SharedPortalManager.playlistItems.filter { it.kind == kind }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, color = KorpLook.TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("${items.size} itens disponíveis", color = KorpLook.TextLow, fontSize = 13.sp)
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(KorpLook.Surf, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum item nesta categoria.", color = KorpLook.TextLow, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items.take(200), key = { it.id }) { item ->
                    ChannelRowItem(item)
                }
            }
        }
    }
}

@Composable
fun ChannelRowItem(item: Item) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KorpLook.Surf)
            .clickable { /* Abrir player no próximo passo */ }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = KorpLook.TextHi,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        Text(
            item.group,
            color = KorpLook.TextLow,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
fun SharedHomeView() {
    val total = SharedPortalManager.playlistItems.size
    val liveCount = SharedPortalManager.playlistItems.count { it.kind == Kind.LIVE }
    val movieCount = SharedPortalManager.playlistItems.count { it.kind == Kind.MOVIE }
    val seriesCount = SharedPortalManager.playlistItems.count { it.kind == Kind.SERIES }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text("Início", color = KorpLook.TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Visão geral do catálogo sincronizado do Portal", color = KorpLook.TextLow, fontSize = 14.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("📺 Canais Ao Vivo", "$liveCount canais", Color(0xFF38BDF8), Modifier.weight(1f))
            StatCard("🎬 Filmes", "$movieCount títulos", Color(0xFFF43F5E), Modifier.weight(1f))
            StatCard("🍿 Séries", "$seriesCount séries", Color(0xFFA855F7), Modifier.weight(1f))
            StatCard("📦 Total", "$total itens", KorpLook.Accent1, Modifier.weight(1f))
        }

        Text("Canais em Destaque", color = KorpLook.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SharedPortalManager.playlistItems.take(50), key = { it.id }) { item ->
                ChannelRowItem(item)
            }
        }
    }
}

@Composable
fun StatCard(title: String, subtitle: String, accentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KorpLook.Surf)
            .border(1.dp, KorpLook.Line, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Text(title, color = KorpLook.TextMid, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = accentColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SharedSettingsView() {
    var portalInput by remember { mutableStateOf(SharedPortalManager.getPortalUrl()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
