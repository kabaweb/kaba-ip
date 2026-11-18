package com.ipvio.stream.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind
import com.ipvio.stream.shared.portal.SharedPortalManager

@Composable
fun SharedSearchScreen(
    onOpenDetail: (Item) -> Unit,
    onPlayChannel: (Item) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") }
    val allItems = SharedPortalManager.playlistItems

    val filters = listOf("Todos", "Filmes", "Séries", "Ao Vivo")

    val results = remember(query, selectedFilter, allItems.size) {
        if (query.trim().length < 2) return@remember emptyList<Item>()
        val q = query.trim().lowercase()

        allItems.filter { item ->
            val matchFilter = when (selectedFilter) {
                "Filmes" -> item.kind == Kind.MOVIE
                "Séries" -> item.kind == Kind.SERIES
                "Ao Vivo" -> item.kind == Kind.LIVE
                else -> true
            }
            if (!matchFilter) return@filter false

            item.title.lowercase().contains(q) || item.group.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Campo de Pesquisa em Destaque
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KorpLook.Surf)
                .border(1.5.dp, if (query.isNotEmpty()) KorpLook.Accent1 else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("🔍", fontSize = 20.sp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                cursorBrush = SolidColor(KorpLook.Accent1),
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "Buscar filmes, séries e canais...",
                            color = Color(0xFFA1A1AA),
                            fontSize = 18.sp
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.weight(1f)
            )

            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { query = "" }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Limpar ✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Filtros por Categoria (Pills)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filters.forEach { f ->
                val active = selectedFilter == f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) KorpLook.Accent1 else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedFilter = f }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = f,
                        color = if (active) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Text(
                    "${results.size} resultados encontrados",
                    color = KorpLook.TextLow,
                    fontSize = 13.sp
                )
            }
        }

        // Resultados da Busca
        if (query.trim().length < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎬", fontSize = 42.sp)
                    Text("O que você quer assistir?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Digite o nome de um filme, série ou canal para pesquisar na sua lista.", color = Color(0xFFA1A1AA), fontSize = 14.sp)
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("❌", fontSize = 36.sp)
                    Text("Nenhum resultado encontrado para \"$query\"", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Verifique se o nome foi digitado corretamente ou mude o filtro.", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                }
            }
        } else {
            // Se for filtro de Canais Ao Vivo, exibe em lista
            if (selectedFilter == "Ao Vivo") {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { ch ->
                        LiveChannelRow(ch, onClick = { onPlayChannel(ch) })
                    }
                }
            } else {
                // Se contiver filmes ou séries, exibe em grade de pôsteres
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results, key = { it.id }) { item ->
                        SharedPosterCard(
                            item = item,
                            onClick = {
                                if (item.kind == Kind.LIVE) {
                                    onPlayChannel(item)
                                } else {
                                    onOpenDetail(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
