package com.galeria.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Barra de rolagem simples para LazyVerticalGrid, exibida do lado direito
 * enquanto o usuário rola a lista, desaparecendo após um curto período de inatividade.
 */
@Composable
fun BoxScope.SimpleVerticalScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isScrolling by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            isScrolling = true
        } else {
            delay(700)
            isScrolling = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isScrolling && totalItems > 0) 0.9f else 0f,
        label = "scrollbarAlpha"
    )

    if (totalItems <= 0) return

    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
    val visibleCount = layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val progress = (firstVisible.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
    val thumbFraction = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.06f, 1f)

    Box(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(20.dp)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
    ) {
        val thumbHeightPx = trackHeightPx * thumbFraction
        val offsetPx = (trackHeightPx - thumbHeightPx) * progress
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { translationY = offsetPx }
                .width(4.dp)
                .height(thumbHeightDp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
                .alpha(alpha)
        )
    }
}
