package com.galeria.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class GaleriaTab(val route: String, val label: String) {
    FOTOS("home", "Fotos"),
    ALBUNS("albums", "Álbuns"),
    FAVORITOS("favorites", "Favoritos")
}

// Altura aproximada da barra (58dp de conteúdo + 6dp de padding em cima e embaixo), sem contar
// o inset da barra de navegação do sistema -- usada pelas telas de Fotos/Álbuns/Favoritos pra
// reservar espaço no final da grade e não deixar a última fileira de fotos escondida atrás da
// barra quando ela estiver opaca.
val GaleriaBottomBarHeight = 70.dp

@Composable
fun GaleriaBottomBar(
    currentRoute: String?,
    opaque: Boolean = true,
    modifier: Modifier = Modifier,
    onSelect: (GaleriaTab) -> Unit
) {
    // Anima suavemente entre opaca (0.88f, como sempre foi) e quase transparente ao rolar as
    // fotos/álbuns pra baixo — 0.06f em vez de 0f pra barra continuar minimamente visível e
    // "tocável" mesmo transparente, em vez de sumir por completo.
    val alpha by animateFloatAsState(
        targetValue = if (opaque) 0.88f else 0.06f,
        animationSpec = tween(durationMillis = 200),
        label = "bottomBarAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
            // Antes essa barra vivia dentro de um Scaffold que cuidava do respiro da barra de
            // navegação do sistema sozinho. Agora que ela flutua livre por cima do conteúdo
            // (pra esse mesmo conteúdo poder aparecer por trás dela), precisa pedir esse
            // respiro na mão, senão fica colada/atrás dos botões de navegação do Android.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .height(58.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GaleriaTab.entries.forEach { tab ->
            BottomBarItem(
                tab = tab,
                icon = when (tab) {
                    GaleriaTab.FOTOS -> Icons.Filled.Photo
                    GaleriaTab.ALBUNS -> Icons.Filled.PhotoAlbum
                    GaleriaTab.FAVORITOS -> Icons.Filled.Star
                },
                selected = currentRoute == tab.route,
                onClick = { onSelect(tab) },
                // Cada item ocupa a mesma largura, garantindo que "Álbuns" fique
                // exatamente no meio entre "Fotos" e "Favoritos", independente do
                // comprimento do texto de cada rótulo.
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: GaleriaTab,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = tab.label, tint = color)
        Text(
            tab.label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
