package com.galeria.ui.screens.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.PhotoGridItem
import com.galeria.ui.components.SimpleVerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: GalleryViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onOpenPhoto: (List<String>, Int) -> Unit,
    onAddPhotos: () -> Unit
) {
    val albumsWithStats by viewModel.albumsWithStats.collectAsState()
    val stats = albumsWithStats.firstOrNull { it.album.id == albumId }
    val favorites by viewModel.favoriteUris.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    val gridState = rememberLazyGridState()

    val photos = remember(stats) { stats?.let { viewModel.resolvePhotos(it.photoUris) } ?: emptyList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stats?.album?.name ?: "Álbum") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Excluir álbum") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                viewModel.deleteAlbum(albumId)
                                onBack()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPhotos) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar fotos")
            }
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma foto neste álbum ainda.")
            }
        } else {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val uriStr = photo.uri.toString()
                        PhotoGridItem(
                            photo = photo,
                            isFavorite = favorites.contains(uriStr),
                            isSelected = selected.value.contains(uriStr),
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    selected.value = if (selected.value.contains(uriStr)) selected.value - uriStr else selected.value + uriStr
                                } else {
                                    val idx = photos.indexOf(photo)
                                    onOpenPhoto(photos.map { it.uri.toString() }, idx)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selected.value = setOf(uriStr)
                            }
                        )
                    }
                }
                SimpleVerticalScrollbar(state = gridState)
            }
        }
    }
}
