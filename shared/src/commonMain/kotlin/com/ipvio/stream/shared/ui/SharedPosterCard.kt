package com.ipvio.stream.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ipvio.stream.shared.model.Item
import com.ipvio.stream.shared.model.Kind

@Composable
fun SharedPosterCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 150.dp,
    onFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "cardScale")
    val isLive = item.kind == Kind.LIVE
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke()
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isLive) 16f / 10f else 2f / 3f)
                .clip(shape)
                .background(KorpLook.Surf2)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) KorpLook.Accent1 else KorpLook.Line,
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Imagem (Logo para TV ou Pôster para Filmes/Séries)
            val imgUrl = item.logo
            if (!imgUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = item.title,
                    modifier = if (isLive) Modifier.fillMaxSize().padding(14.dp) else Modifier.fillMaxSize(),
                    contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop
                )
            } else {
                // Placeholder quando não tem imagem
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.title.take(2).uppercase(),
                        color = KorpLook.Accent1,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badge no topo do card
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (item.kind) {
                        Kind.LIVE -> "LIVE"
                        Kind.MOVIE -> "FILME"
                        Kind.SERIES -> "SÉRIE"
                    },
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!item.quality.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KorpLook.Accent1.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.quality,
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Título e Categoria abaixo do card
        Text(
            text = item.title,
            color = if (isFocused) Color.White else KorpLook.TextHi,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = if (item.kind == Kind.SERIES && item.episodes.isNotEmpty()) {
            val sCount = item.episodes.map { it.season }.distinct().size
            "${sCount} Temp • ${item.episodes.size} Eps"
        } else {
            item.group
        }
        Text(
            text = subtitle,
            color = if (item.kind == Kind.SERIES && item.episodes.isNotEmpty()) KorpLook.Accent1 else KorpLook.TextLow,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
