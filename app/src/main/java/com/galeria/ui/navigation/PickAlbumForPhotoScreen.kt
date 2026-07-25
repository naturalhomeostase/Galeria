package com.galeria.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.screens.albums.CreateAlbumDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickAlbumForPhotoScreen(
    viewModel: GalleryViewModel,
    photoUri: String,
    onDone: () -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar a álbum") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxWidth()) {
            item {
                ListItem(
                    headlineContent = { Text("Criar novo álbum") },
                    leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                    modifier = Modifier.clickable { showCreateDialog = true }
                )
            }
            items(albums, key = { it.id }) { album ->
                ListItem(
                    headlineContent = { Text(album.name) },
                    leadingContent = { Icon(Icons.Filled.PhotoAlbum, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.addPhotoToAlbum(album.id, photoUri)
                        onDone()
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, isSecret ->
                showCreateDialog = false
                viewModel.createAlbum(name, isSecret) { id ->
                    viewModel.addPhotoToAlbum(id, photoUri)
                    onDone()
                }
            }
        )
    }
}
