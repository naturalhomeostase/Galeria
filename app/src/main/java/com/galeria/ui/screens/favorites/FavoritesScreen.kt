package com.galeria.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.AlbumPickerDialog
import com.galeria.ui.components.MoveToDeviceFolderDialog
import com.galeria.ui.components.PhotoGridItem
import com.galeria.ui.components.SelectionActionBar
import com.galeria.ui.components.SimpleVerticalScrollbar
import com.galeria.ui.screens.albums.CreateAlbumDialog
import com.galeria.util.rememberBulkDeleteAction
import com.galeria.util.rememberMoveToFolderAction
import com.galeria.util.shareMultiplePhotos
import com.galeria.util.ObserveGridScrollForBottomBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: GalleryViewModel,
    onOpenPhoto: (List<String>, Int) -> Unit
) {
    val context = LocalContext.current
    val favorites by viewModel.favoriteUris.collectAsState()
    val allPhotos by viewModel.allPhotos.collectAsState()
    val albumsWithStats by viewModel.albumsWithStats.collectAsState()
    val deviceFolders by viewModel.deviceFolders.collectAsState()
    val photos = remember(favorites, allPhotos) {
        allPhotos.filter { favorites.contains(it.uri.toString()) }
    }
    val gridState = rememberLazyGridState()
    ObserveGridScrollForBottomBar(gridState = gridState, viewModel = viewModel)

    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showCreateForCopy by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selected.value = emptySet()
    }

    val bulkDelete = rememberBulkDeleteAction(onCompleted = {
        exitSelection()
        // Sem isso, a foto/vídeo continuava aparecendo na grade depois de excluído --
        // a lista em memória só era recarregada ao reabrir o app.
        viewModel.loadPhotos()
    })

    val moveToFolder = rememberMoveToFolderAction(viewModel = viewModel, onCompleted = { failures ->
        exitSelection()
        if (failures > 0) {
            android.widget.Toast.makeText(
                context,
                if (failures == 1) "1 item não pôde ser movido" else "$failures itens não puderam ser movidos",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    })

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Favoritos") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (photos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Toque na estrela de uma foto para adicioná-la aqui.")
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
                                isFavorite = true,
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
                                    selected.value = selected.value + uriStr
                                }
                            )
                        }
                    }
                    SimpleVerticalScrollbar(state = gridState)
                }
            }

            if (selectionMode && selected.value.isNotEmpty()) {
                SelectionActionBar(
                    selectedCount = selected.value.size,
                    onClearSelection = { exitSelection() },
                    onFavorite = {
                        viewModel.setFavorites(selected.value, false)
                        exitSelection()
                    },
                    onShare = { shareMultiplePhotos(context, selected.value.toList()) },
                    onDelete = { bulkDelete(selected.value.toList()) },
                    onCopyToAlbum = { showCopyDialog = true },
                    onMoveToDeviceFolder = { showMoveToFolderDialog = true }
                )
            }
        }
    }

    if (showCopyDialog) {
        AlbumPickerDialog(
            title = "Copiar para álbum",
            albums = albumsWithStats.map { it.album },
            onDismiss = { showCopyDialog = false },
            onPick = { album ->
                viewModel.addPhotosToAlbum(album.id, selected.value)
                showCopyDialog = false
                exitSelection()
            },
            onCreateNew = {
                showCopyDialog = false
                showCreateForCopy = true
            }
        )
    }

    if (showCreateForCopy) {
        CreateAlbumDialog(
            onDismiss = { showCreateForCopy = false },
            onConfirm = { name, isSecret ->
                showCreateForCopy = false
                viewModel.createAlbum(name, isSecret) { newId ->
                    viewModel.addPhotosToAlbum(newId, selected.value)
                    exitSelection()
                }
            }
        )
    }

    if (showMoveToFolderDialog) {
        MoveToDeviceFolderDialog(
            existingFolderNames = deviceFolders.map { it.name },
            onDismiss = { showMoveToFolderDialog = false },
            onConfirm = { targetFolder ->
                showMoveToFolderDialog = false
                moveToFolder(selected.value.toList(), targetFolder)
            }
        )
    }
}
