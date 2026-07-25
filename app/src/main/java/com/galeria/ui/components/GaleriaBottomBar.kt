package com.galeria.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class GaleriaTab(val route: String, val label: String) {
    FOTOS("home", "Fotos"),
    ALBUNS("albums", "Álbuns"),
    FAVORITOS("favorites", "Favoritos")
}

@Composable
fun GaleriaBottomBar(currentRoute: String?, onSelect: (GaleriaTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == GaleriaTab.FOTOS.route,
            onClick = { onSelect(GaleriaTab.FOTOS) },
            icon = { Icon(Icons.Filled.Photo, contentDescription = null) },
            label = { Text(GaleriaTab.FOTOS.label) }
        )
        NavigationBarItem(
            selected = currentRoute == GaleriaTab.ALBUNS.route,
            onClick = { onSelect(GaleriaTab.ALBUNS) },
            icon = { Icon(Icons.Filled.PhotoAlbum, contentDescription = null) },
            label = { Text(GaleriaTab.ALBUNS.label) }
        )
        NavigationBarItem(
            selected = currentRoute == GaleriaTab.FAVORITOS.route,
            onClick = { onSelect(GaleriaTab.FAVORITOS) },
            icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            label = { Text(GaleriaTab.FAVORITOS.label) }
        )
    }
}
