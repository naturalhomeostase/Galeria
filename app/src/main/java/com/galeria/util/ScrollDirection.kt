package com.galeria.util

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.galeria.ui.GalleryViewModel

/**
 * Observa a direção da rolagem de uma grade (Fotos/Álbuns/Favoritos), tanto pra:
 * 1) deixar a barra de baixo transparente enquanto o usuário rola pra baixo e opaca de novo ao
 *    rolar pra cima ou chegar no topo;
 * 2) lembrar a posição de rolagem daquela tela (via [scopeKey]) pra restaurar depois -- sem
 *    isso, voltar de uma foto ou de um álbum sempre jogava a grade de volta pro topo, mesmo a
 *    foto/álbum estando lá embaixo.
 *
 * O [scopeKey] é opcional só pra não quebrar quem já chamava essa função sem esse parâmetro;
 * sem ele, a posição simplesmente não é lembrada.
 */
@Composable
fun ObserveGridScrollForBottomBar(gridState: LazyGridState, viewModel: GalleryViewModel, scopeKey: String? = null) {
    var previousIndex by remember(gridState) { mutableStateOf(gridState.firstVisibleItemIndex) }
    var previousOffset by remember(gridState) { mutableStateOf(gridState.firstVisibleItemScrollOffset) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (scopeKey != null) {
                    viewModel.setScrollPosition(scopeKey, index, offset)
                }

                val atTop = index == 0 && offset == 0
                val scrollingForward = index > previousIndex || (index == previousIndex && offset > previousOffset)
                val scrollingBackward = index < previousIndex || (index == previousIndex && offset < previousOffset)
                when {
                    atTop -> viewModel.setBottomBarOpaque(true)
                    scrollingForward -> viewModel.setBottomBarOpaque(false)
                    scrollingBackward -> viewModel.setBottomBarOpaque(true)
                }
                previousIndex = index
                previousOffset = offset
            }
    }
}
