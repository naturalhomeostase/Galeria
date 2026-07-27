package com.galeria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Barra de ações exibida quando há itens selecionados numa grade de fotos.
 * onMoveToAlbum é nulo quando não há um álbum de origem (ex.: tela de Fotos ou Favoritos),
 * nesse caso a ação "mover" fica oculta e só "copiar para álbum" aparece.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopyToAlbum: () -> Unit,
    onMoveToAlbum: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção")
            }
            Text("$selectedCount selecionada${if (selectedCount == 1) "" else "s"}")
        }
        Row {
            IconButton(onClick = onFavorite) {
                Icon(Icons.Filled.Star, contentDescription = "Favoritar")
            }
            IconButton(onClick = onCopyToAlbum) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar para álbum")
            }
            if (onMoveToAlbum != null) {
                IconButton(onClick = onMoveToAlbum) {
                    Icon(Icons.Filled.DriveFileMove, contentDescription = "Mover para álbum")
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Compartilhar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Mover para lixeira")
            }
        }
    }
}
