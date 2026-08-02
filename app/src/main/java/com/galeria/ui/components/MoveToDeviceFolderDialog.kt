package com.galeria.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Diálogo pra escolher a pasta física de destino no "Mover para pasta" (diferente do
 * "Mover/Copiar para álbum", que só mexe em marcação virtual dentro do app — aqui o arquivo
 * realmente muda de lugar no armazenamento).
 */
@Composable
fun MoveToDeviceFolderDialog(
    existingFolderNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover para pasta") },
        text = {
            Column {
                Text(
                    "O arquivo sai de onde está e passa a existir só na pasta escolhida — " +
                        "diferente de \"copiar/mover para álbum\", que não mexe no arquivo em si.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nova pasta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existingFolderNames.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("ou escolha uma pasta existente", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.heightIn(max = 260.dp)) {
                        LazyColumn {
                            items(existingFolderNames, key = { it }) { name ->
                                ListItem(
                                    headlineContent = { Text(name) },
                                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                    modifier = Modifier.clickable { onConfirm(name) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newFolderName.trim()) },
                enabled = newFolderName.isNotBlank()
            ) { Text("Mover") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
