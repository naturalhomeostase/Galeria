package com.galeria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Barra de ações exibida ao selecionar um ou mais álbuns/pastas na tela de Álbuns.
 * onDelete é nulo quando a seleção contém apenas pastas do dispositivo (que não podem
 * ser excluídas, só ocultadas).
 */
@Composable
fun AlbumSelectionActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onToggleHidden: () -> Unit,
    hideLabel: String,
    hideIcon: Boolean,
    onDelete: (() -> Unit)?
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
            Text("$selectedCount selecionado${if (selectedCount == 1) "" else "s"}")
        }
        Row {
            IconButton(onClick = onToggleHidden) {
                Icon(
                    imageVector = if (hideIcon) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = hideLabel
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir álbum")
                }
            }
        }
    }
}
