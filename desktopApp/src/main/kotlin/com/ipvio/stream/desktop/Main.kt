package com.ipvio.stream.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ipvio.stream.shared.KmpInfo

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Korp TV - Windows Desktop (KMP)"
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            DesktopAppContent()
        }
    }
}

@Composable
fun DesktopAppContent() {
    var selectedTab by remember { mutableStateOf("home") }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Sidebar Navigation
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E293B))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo / Header
            Column {
                Text(
                    text = "KORP TV",
                    color = Color(0xFF6366F1),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Desktop Edition (Windows)",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Navigation Items
            NavButton("📺  Canais Ao Vivo", selectedTab == "live") { selectedTab = "live" }
            NavButton("🎬  Filmes (VOD)", selectedTab == "movies") { selectedTab = "movies" }
            NavButton("🍿  Séries", selectedTab == "series") { selectedTab = "series" }
            NavButton("📁  Coleções & Sagas", selectedTab == "collections") { selectedTab = "collections" }
            NavButton("⚙️  Configurações", selectedTab == "settings") { selectedTab = "settings" }

            Spacer(Modifier.weight(1f))

            // KMP Diagnostic badge
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Core Status:", color = Color.Gray, fontSize = 11.sp)
                    Text(KmpInfo.info(), color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D16))
                .padding(32.dp)
        ) {
            when (selectedTab) {
                "live" -> SectionView("Canais Ao Vivo", "Player nativo de canais em transmissão HLS/TS em tempo real.")
                "movies" -> SectionView("Filmes (VOD)", "Catálogo de filmes sincronizado via Xtream Codes / M3U com metadados TMDB.")
                "series" -> SectionView("Séries", "Temporadas e episódios sincronizados.")
                "collections" -> SectionView("Coleções & Sagas", "Franquias completas agrupadas em ordem cronológica (Star Wars, Harry Potter, etc.).")
                "settings" -> SettingsView()
                else -> SectionView("Início", "Bem-vindo ao Korp TV Desktop.")
            }
        }
    }
}

@Composable
fun NavButton(text: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFF6366F1) else Color.Transparent,
            contentColor = if (active) Color.White else Color(0xFF94A3B8)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(text, fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
fun SectionView(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.Gray, fontSize = 14.sp)

        Card(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Módulo KMP Desktop Ativo\nPronto para conectar listas e player",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SettingsView() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Configurações do Korp TV", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Gerencie os servidores IPTV e o Portal Remoto.", color = Color.Gray, fontSize = 14.sp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Portal Korp TV", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Endereço do Portal: http://192.168.1.99:3002", color = Color.LightGray, fontSize = 14.sp)
                Text("Plataforma Atual: " + KmpInfo.info(), color = Color(0xFF6366F1), fontSize = 14.sp)
            }
        }
    }
}
