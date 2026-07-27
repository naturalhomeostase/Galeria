package com.galeria.ui.screens.largefiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.AlbumPickerDialog
import com.galeria.ui.components.SelectionActionBar
import com.galeria.ui.screens.albums.CreateAlbumDialog
import com.galeria.util.FileUtils
import com.galeria.util.rememberBulkDeleteAction
import com.galeria.util.shareMultiplePhotos

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LargeFilesScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenPhoto: (List<String>, Int) -> Unit
) {
    val context = LocalContext.current
    val allPhotos by viewModel.allPhotos.collectAsState()
    val favorites by viewModel.favoriteUris.collectAsState()
    val albumsWithStats by viewModel.albumsWithStats.collectAsState()

    val sortedPhotos = remember(allPhotos) { allPhotos.sortedByDescending { it.sizeBytes } }
    val allUris = remember(sortedPhotos) { sortedPhotos.map { it.uri.toString() } }

    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showCreateForCopy by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selected.value = emptySet()
    }

    val bulkDelete = rememberBulkDeleteAction(onCompleted = { exitSelection() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arquivos grandes") },
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) exitSelection() else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (sortedPhotos.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma foto encontrada.")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(sortedPhotos, key = { it.id }) { photo ->
                        val uriStr = photo.uri.toString()
                        val isSelected = selected.value.contains(uriStr)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            selected.value = if (isSelected) selected.value - uriStr else selected.value + uriStr
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
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(photo.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        FileUtils.formatSize(photo.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (favorites.contains(uriStr)) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.padding(start = 6.dp).size(14.dp)
                                        )
                                    }
                                }
                            }
                            if (selectionMode) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                        Divider()
                    }
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
                    onShare = { shareMultiplePhotos(context, selected.value.toList()) },
                    onDelete = { bulkDelete(selected.value.toList()) },
                    onCopyToAlbum = { showCopyDialog = true }
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
}
