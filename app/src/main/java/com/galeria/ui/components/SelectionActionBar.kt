package com.galeria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Barra de ações exibida quando há itens selecionados numa grade de fotos.
 * onMoveToAlbum é nulo quando não há um álbum de origem (ex.: tela de Fotos ou Favoritos),
 * nesse caso a ação "mover" fica oculta e só "copiar para álbum" aparece.
 *
 * Cada ação tem um rótulo de texto abaixo do ícone (não só o ícone) para deixar claro
 * o que cada botão faz, já que "copiar/mover para álbum" nem sempre é óbvio só pelo ícone.
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção")
            }
            Text("$selectedCount selecionada${if (selectedCount == 1) "" else "s"}")
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionBarItem(Icons.Filled.Star, "Favoritar", onFavorite)
            ActionBarItem(Icons.Filled.ContentCopy, "Copiar p/ álbum", onCopyToAlbum)
            if (onMoveToAlbum != null) {
                ActionBarItem(Icons.Filled.DriveFileMove, "Mover p/ álbum", onMoveToAlbum)
            }
            ActionBarItem(Icons.Filled.Share, "Compartilhar", onShare)
            ActionBarItem(Icons.Filled.Delete, "Lixeira", onDelete)
        }
    }
}

@Composable
private fun ActionBarItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 60.dp)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
    }
}
