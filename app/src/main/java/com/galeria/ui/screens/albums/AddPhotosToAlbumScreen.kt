package com.galeria.ui.screens.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.PhotoGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPhotosToAlbumScreen(
    viewModel: GalleryViewModel,
    albumId: Long,
    onDone: () -> Unit
) {
    val allPhotos by viewModel.allPhotos.collectAsState()
    val favorites by viewModel.favoriteUris.collectAsState()
    val selected = remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Escolher fotos") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        selected.value.forEach { uri -> viewModel.addPhotoToAlbum(albumId, uri) }
                        onDone()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Concluir")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            items(allPhotos, key = { it.id }) { photo ->
                val uriStr = photo.uri.toString()
                PhotoGridItem(
                    photo = photo,
                    isFavorite = favorites.contains(uriStr),
                    isSelected = selected.value.contains(uriStr),
                    selectionMode = true,
                    onClick = {
                        selected.value = if (selected.value.contains(uriStr)) selected.value - uriStr else selected.value + uriStr
                    },
                    onLongClick = {}
                )
            }
        }
    }
}
