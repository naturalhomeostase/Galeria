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
import androidx.compose.material.icons.filled.Search
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
 * "Copiar para álbum", que só mexe em marcação virtual dentro do app — aqui o arquivo
 * realmente muda de lugar no armazenamento).
 *
 * O campo de texto serve pras duas coisas ao mesmo tempo: filtra a lista de pastas já
 * existentes enquanto digita, e vira o nome de uma pasta nova caso nenhuma bata certinho —
 * assim não tem como criar sem querer uma pasta duplicada com o mesmo nome de uma que já
 * existe (ex.: digitando "Camera" com essa pasta já existindo, o botão de confirmar já usa a
 * pasta existente em vez de tentar criar outra igual).
 */
@Composable
fun MoveToDeviceFolderDialog(
    existingFolderNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val trimmedQuery = query.trim()
    val exactMatch = remember(trimmedQuery, existingFolderNames) {
        existingFolderNames.firstOrNull { it.equals(trimmedQuery, ignoreCase = true) }
    }
    val filteredFolders = remember(trimmedQuery, existingFolderNames) {
        if (trimmedQuery.isBlank()) {
            existingFolderNames
        } else {
            existingFolderNames.filter { it.contains(trimmedQuery, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover para pasta") },
        text = {
            Column {
                Text(
                    "O arquivo sai de onde está e passa a existir só na pasta escolhida — " +
                        "diferente de \"copiar para álbum\", que não mexe no arquivo em si.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar ou criar pasta") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                when {
                    filteredFolders.isNotEmpty() -> {
                        Text(
                            if (trimmedQuery.isBlank()) "Pastas existentes" else "Pastas encontradas",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Box(Modifier.heightIn(max = 220.dp)) {
                            LazyColumn {
                                items(filteredFolders, key = { it }) { name ->
                                    ListItem(
                                        headlineContent = { Text(name) },
                                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                        modifier = Modifier.clickable { onConfirm(name) }
                                    )
                                }
                            }
                        }
                    }
                    trimmedQuery.isNotBlank() -> {
                        Text(
                            "Nenhuma pasta com esse nome — uma pasta nova será criada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(exactMatch ?: trimmedQuery) },
                enabled = trimmedQuery.isNotBlank()
            ) {
                Text(if (exactMatch != null) "Mover" else "Criar e mover")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
