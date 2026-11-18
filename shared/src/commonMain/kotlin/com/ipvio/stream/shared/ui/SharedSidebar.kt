package com.ipvio.stream.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SharedMenuIcon(id: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = 2.dp.toPx()
        when (id) {
            0 -> { // Home
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(s * 0.5f, s * 0.15f)
                    lineTo(s * 0.85f, s * 0.45f)
                    lineTo(s * 0.85f, s * 0.85f)
                    lineTo(s * 0.15f, s * 0.85f)
                    lineTo(s * 0.15f, s * 0.45f)
                    close()
                }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            1 -> { // Filme
                drawRoundRect(color, Offset(s * 0.15f, s * 0.2f), Size(s * 0.7f, s * 0.6f), CornerRadius(s * 0.1f), style = Stroke(stroke))
                drawLine(color, Offset(s * 0.35f, s * 0.2f), Offset(s * 0.35f, s * 0.8f), stroke)
                drawLine(color, Offset(s * 0.65f, s * 0.2f), Offset(s * 0.65f, s * 0.8f), stroke)
            }
            2 -> { // Serien
                drawRoundRect(color, Offset(s * 0.15f, s * 0.25f), Size(s * 0.7f, s * 0.55f), CornerRadius(s * 0.1f), style = Stroke(stroke))
                drawLine(color, Offset(s * 0.35f, s * 0.15f), Offset(s * 0.65f, s * 0.15f), stroke, cap = StrokeCap.Round)
            }
            3 -> { // Live TV
                drawRoundRect(color, Offset(s * 0.15f, s * 0.25f), Size(s * 0.7f, s * 0.5f), CornerRadius(s * 0.08f), style = Stroke(stroke))
                drawLine(color, Offset(s * 0.35f, s * 0.85f), Offset(s * 0.65f, s * 0.85f), stroke)
                drawLine(color, Offset(s * 0.5f, s * 0.75f), Offset(s * 0.5f, s * 0.85f), stroke)
            }
            5 -> { // Configurações
                drawCircle(color, s * 0.35f, Offset(s * 0.5f, s * 0.5f), style = Stroke(stroke))
                drawCircle(color, s * 0.15f, Offset(s * 0.5f, s * 0.5f), style = Stroke(stroke))
            }
            else -> {
                drawCircle(color, s * 0.3f, Offset(s * 0.5f, s * 0.5f), style = Stroke(stroke))
            }
        }
    }
}

data class MenuItemDef(val id: Int, val label: String)

val DEFAULT_APP_TABS = listOf(
    MenuItemDef(0, "Início"),
    MenuItemDef(3, "Canais Ao Vivo"),
    MenuItemDef(1, "Filmes"),
    MenuItemDef(2, "Séries"),
    MenuItemDef(5, "Configurações")
)

@Composable
fun SharedSidebar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .width(if (expanded) 230.dp else 74.dp)
            .fillMaxHeight()
            .background(KorpLook.Surf)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Logo
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KorpLook.Accent1),
                contentAlignment = Alignment.Center
            ) {
                Text("K", color = KorpLook.OnAccent, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("KORP TV", color = KorpLook.TextHi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Multiplataforma", color = KorpLook.TextLow, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        DEFAULT_APP_TABS.forEach { tab ->
            val active = selectedTab == tab.id
            val bg = if (active) KorpLook.Surf2 else Color.Transparent
            val fg = if (active) KorpLook.Accent1 else KorpLook.TextMid

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelectTab(tab.id) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SharedMenuIcon(tab.id, fg)
                if (expanded) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        tab.label,
                        color = if (active) KorpLook.TextHi else KorpLook.TextMid,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Toggle Expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (expanded) "◀ Recolher" else "▶", color = KorpLook.TextLow, fontSize = 12.sp)
        }
    }
}
