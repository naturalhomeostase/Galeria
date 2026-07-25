package com.galeria.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.PhotoGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: GalleryViewModel,
    onOpenPhoto: (List<String>, Int) -> Unit
) {
    val favorites by viewModel.favoriteUris.collectAsState()
    val allPhotos by viewModel.allPhotos.collectAsState()
    val photos = remember(favorites, allPhotos) {
        allPhotos.filter { favorites.contains(it.uri.toString()) }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Favoritos") }) }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Toque na estrela de uma foto para adicioná-la aqui.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val uriStr = photo.uri.toString()
                    PhotoGridItem(
                        photo = photo,
                        isFavorite = true,
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
        }
    }
}
