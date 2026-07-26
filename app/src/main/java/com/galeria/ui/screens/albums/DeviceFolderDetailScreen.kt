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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.PhotoGridItem
import com.galeria.ui.components.SimpleVerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceFolderDetailScreen(
    viewModel: GalleryViewModel,
    folderName: String,
    onBack: () -> Unit,
    onOpenPhoto: (List<String>, Int) -> Unit
) {
    val allPhotos by viewModel.allPhotos.collectAsState()
    val favorites by viewModel.favoriteUris.collectAsState()
    val gridState = rememberLazyGridState()
    val photos = remember(allPhotos, folderName) { allPhotos.filter { it.bucketName == folderName } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma foto nesta pasta.")
                }
            } else {
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
                            isSelected = false,
                            selectionMode = false,
                            onClick = {
                                val idx = photos.indexOf(photo)
                                onOpenPhoto(photos.map { it.uri.toString() }, idx)
                            },
                            onLongClick = {}
                        )
                    }
                }
                SimpleVerticalScrollbar(state = gridState)
            }
        }
    }
}
