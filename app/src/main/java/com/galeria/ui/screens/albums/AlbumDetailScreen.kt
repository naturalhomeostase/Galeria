package com.galeria.ui.screens.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.PhotoSortOption
import com.galeria.ui.components.AlbumPickerDialog
import com.galeria.ui.components.MonthHeader
import com.galeria.ui.components.MoveToDeviceFolderDialog
import com.galeria.ui.components.PhotoGridItem
import com.galeria.ui.components.SelectionActionBar
import com.galeria.ui.components.SimpleVerticalScrollbar
import com.galeria.ui.groupPhotosByMonthUtil
import com.galeria.ui.sortPhotos
import com.galeria.util.rememberBulkDeleteAction
import com.galeria.util.rememberMoveToFolderAction
import com.galeria.util.shareMultiplePhotos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: GalleryViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onOpenPhoto: (List<String>, Int) -> Unit,
    onAddPhotos: () -> Unit
) {
    val context = LocalContext.current
    val albumsWithStats by viewModel.albumsWithStats.collectAsState()
    val gridColumns by viewModel.photoGridColumns.collectAsState()
    val deviceFolders by viewModel.deviceFolders.collectAsState()
    val stats = albumsWithStats.firstOrNull { it.album.id == albumId }
    val favorites by viewModel.favoriteUris.collectAsState()
    var sortOption by remember(albumId) { mutableStateOf(viewModel.getPhotoSortOptionFor("album_$albumId")) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    val gridState = rememberLazyGridState()
    var showCopyDialog by remember { mutableStateOf(false) }
    var showCreateForCopy by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }

    val photos = remember(stats) { stats?.let { viewModel.resolvePhotos(it.photoUris) } ?: emptyList() }
    val isDateSort = sortOption == PhotoSortOption.RECENTE || sortOption == PhotoSortOption.ANTIGA
    val monthGroups = remember(photos, sortOption) {
        if (isDateSort) groupPhotosByMonthUtil(photos, descending = sortOption == PhotoSortOption.RECENTE) else emptyList()
    }
    val flatPhotos = remember(photos, sortOption) {
        if (!isDateSort) sortPhotos(photos, sortOption) else emptyList()
    }
    val allUris = remember(photos, sortOption) {
        if (isDateSort) monthGroups.flatMap { g -> g.photos.map { it.uri.toString() } } else flatPhotos.map { it.uri.toString() }
    }

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
        topBar = {
            TopAppBar(
                title = { Text(stats?.album?.name ?: "Álbum") },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = { if (selectionMode) exitSelection() else onBack() },
                        modifier = Modifier.padding(start = 8.dp).size(38.dp)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (!selectionMode) {
                        FilledTonalIconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Filled.Sort, contentDescription = "Ordenar por")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            PhotoSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sortOption = option
                                        viewModel.setPhotoSortOptionFor("album_$albumId", option)
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.moveAlbumToTrash(albumId)
                            onBack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Mover álbum para a lixeira")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onAddPhotos) {
                    Icon(Icons.Filled.Add, contentDescription = "Adicionar fotos")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (photos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhuma foto neste álbum ainda.")
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        if (isDateSort) {
                            monthGroups.forEach { group ->
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
                                                selected.value = if (selected.value.contains(uriStr)) selected.value - uriStr else selected.value + uriStr
                                            } else {
                                                val idx = allUris.indexOf(uriStr)
                                                onOpenPhoto(allUris, idx)
                                            }
                                        },
                                        onLongClick = {
                                            selectionMode = true
                                            selected.value = selected.value + uriStr
                                        }
                                    )
                                }
                            }
                        } else {
                            items(flatPhotos, key = { it.id }) { photo ->
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
                                            val idx = allUris.indexOf(uriStr)
                                            onOpenPhoto(allUris, idx)
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selected.value = selected.value + uriStr
                                    }
                                )
                            }
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
                        viewModel.setFavorites(selected.value, true)
                        exitSelection()
                    },
                    onShare = {
                        shareMultiplePhotos(context, selected.value.toList())
                    },
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
            albums = albumsWithStats.map { it.album }.filter { it.id != albumId },
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
