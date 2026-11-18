package com.ipvio.stream.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipvio.stream.shared.KmpInfo

@Composable
fun SharedApp() {
    var selectedTab by remember { mutableStateOf(0) }

    Row(modifier = Modifier.fillMaxSize().background(KorpLook.Bg)) {
        SharedSidebar(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KorpLook.Bg)
                .padding(28.dp)
        ) {
            when (selectedTab) {
                0 -> SharedHomeView()
                1 -> SharedCatalogView("Filmes (VOD)", "Catálogo completo de filmes com suporte a sagas e TMDB.")
                2 -> SharedCatalogView("Séries", "Séries completas organizadas por temporada e episódios.")
                3 -> SharedCatalogView("Canais Ao Vivo", "Guia de canais e transmissão de TV em tempo real.")
                5 -> SharedSettingsView()
                else -> SharedHomeView()
            }
        }
    }
}

@Composable
fun SharedHomeView() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Início", color = KorpLook.TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Destaques e títulos assistidos recentemente", color = KorpLook.TextLow, fontSize = 14.sp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(KorpLook.Surf, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Korp TV — Núcleo Multiplataforma Ativo", color = KorpLook.Accent1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Ambiente: " + KmpInfo.info(), color = KorpLook.TextMid, fontSize = 13.sp)
                Text("A mesma interface e regras de negócio do app Android agora rodando no Desktop e pronta para expansão.", color = KorpLook.TextLow, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SharedCatalogView(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, color = KorpLook.TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(description, color = KorpLook.TextLow, fontSize = 14.sp)
    }
}

@Composable
fun SharedSettingsView() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Configurações", color = KorpLook.TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Servidores IPTV, Portal Remoto e Preferências de Reprodução.", color = KorpLook.TextLow, fontSize = 14.sp)
    }
}
