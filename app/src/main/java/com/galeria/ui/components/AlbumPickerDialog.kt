package com.galeria.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.data.model.AlbumEntity

@Composable
fun AlbumPickerDialog(
    title: String,
    albums: List<AlbumEntity>,
    onDismiss: () -> Unit,
    onPick: (AlbumEntity) -> Unit,
    onCreateNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Criar novo álbum") },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onCreateNew)
                    )
                }
                items(albums, key = { it.id }) { album ->
                    ListItem(
                        headlineContent = { Text(album.name) },
                        leadingContent = { Icon(Icons.Filled.PhotoAlbum, contentDescription = null) },
                        modifier = Modifier.clickable { onPick(album) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
