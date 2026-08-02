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
 * Observa a direção da rolagem de uma grade (Fotos/Álbuns/Favoritos) e avisa o ViewModel pra
 * deixar a barra de baixo transparente enquanto o usuário rola pra baixo (vendo mais fotos) e
 * opaca de novo ao rolar de volta pra cima ou ao chegar no topo da lista.
 */
@Composable
fun ObserveGridScrollForBottomBar(gridState: LazyGridState, viewModel: GalleryViewModel) {
    var previousIndex by remember(gridState) { mutableStateOf(gridState.firstVisibleItemIndex) }
    var previousOffset by remember(gridState) { mutableStateOf(gridState.firstVisibleItemScrollOffset) }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
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
