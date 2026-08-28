package com.galeria.ui.screens.picker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galeria.data.model.Photo
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.PhotoGridItem

/**
 * Tela mostrada quando o Galeria é aberto por OUTRO app (ex.: escolher uma imagem para
 * upload num site, anexar num e-mail, etc.) via ACTION_GET_CONTENT ou ACTION_PICK.
 * Em vez do fluxo normal de navegação do app, aqui o toque numa foto já a devolve para
 * quem pediu (ou, no caso de seleção múltipla, acumula a seleção até "Concluir").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPickerScreen(
    viewModel: GalleryViewModel,
    allowMultiple: Boolean,
    filter: (Photo) -> Boolean,
    onPick: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler(onBack = onCancel)

    val allPhotos by viewModel.allPhotos.collectAsState()
    val gridColumns by viewModel.photoGridColumns.collectAsState()
    val favorites by viewModel.favoriteUris.collectAsState()
    val photos = remember(allPhotos) { allPhotos.filter(filter) }

    // Lista (não Set) só pra manter a ordem em que a pessoa tocou nas fotos -- útil se quem
    // pediu a seleção múltipla se importar com a ordem dos itens retornados.
    val selected = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (allowMultiple) "Selecionar imagens" else "Selecionar imagem") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                    }
                },
                actions = {
                    if (allowMultiple) {
                        TextButton(
                            onClick = { onPick(selected.map { Uri.parse(it) }) },
                            enabled = selected.isNotEmpty()
                        ) {
                            Text(if (selected.isEmpty()) "Concluir" else "Concluir (${selected.size})")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhuma imagem encontrada",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val uriStr = photo.uri.toString()
                    PhotoGridItem(
                        photo = photo,
                        isFavorite = favorites.contains(uriStr),
                        isSelected = selected.contains(uriStr),
                        selectionMode = allowMultiple,
                        onClick = {
                            if (allowMultiple) {
                                if (selected.contains(uriStr)) selected.remove(uriStr) else selected.add(uriStr)
                            } else {
                                onPick(listOf(photo.uri))
                            }
                        },
                        onLongClick = {
                            if (allowMultiple && !selected.contains(uriStr)) selected.add(uriStr)
                        }
                    )
                }
            }
        }
    }
}
