package com.galeria.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.MonthHeader
import com.galeria.ui.components.PhotoGridItem
import com.galeria.ui.components.SimpleVerticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    onOpenPhoto: (List<String>, Int) -> Unit
) {
    val groups by viewModel.monthGroups.collectAsState()
    val favorites by viewModel.favoriteUris.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    val allUris = remember(groups) { groups.flatMap { g -> g.photos.map { it.uri.toString() } } }
    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fotos") }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                groups.forEach { group ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MonthHeader(group.label, group.photos.size)
                    }
                    items(group.photos, key = { it.id }) { photo ->
                        val uriStr = photo.uri.toString()
                        PhotoGridItem(
                            photo = photo,
                            isFavorite = favorites.contains(uriStr),
                            isSelected = selected.value.contains(uriStr),
                            selectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    selected.value = toggle(selected.value, uriStr)
                                } else {
                                    val idx = allUris.indexOf(uriStr)
                                    onOpenPhoto(allUris, idx)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selected.value = toggle(selected.value, uriStr)
                            }
                        )
                    }
                }
            }
            SimpleVerticalScrollbar(state = gridState)
            }
        }
    }
}

private fun toggle(set: Set<String>, item: String): Set<String> =
    if (set.contains(item)) set - item else set + item

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text("Nenhuma foto encontrada")
        }
    }
}
