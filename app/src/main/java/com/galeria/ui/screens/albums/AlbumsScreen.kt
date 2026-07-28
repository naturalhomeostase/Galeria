package com.galeria.ui.screens.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.galeria.ui.AlbumSortOption
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.AlbumSelectionActionBar
import com.galeria.ui.components.SimpleVerticalScrollbar
import com.galeria.ui.sortAlbums
import com.galeria.ui.sortDeviceFolders
import com.galeria.util.FileUtils

/** Representa um item selecionável (álbum real ou pasta do dispositivo) na grade. */
private data class SelectableAlbum(
    val key: String,
    val albumId: Long?,
    val folderName: String?,
    val isHidden: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    viewModel: GalleryViewModel,
    onOpenAlbum: (Long, Boolean) -> Unit,
    onOpenDeviceFolder: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val albumsWithStats by viewModel.albumsWithStats.collectAsState()
    val deviceFolders by viewModel.deviceFolders.collectAsState()
    val showHidden by viewModel.showHiddenAlbums.collectAsState()
    val sortOption by viewModel.albumSortOption.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun exitSelection() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    fun toggleSelection(key: String) {
        selectedKeys = if (selectedKeys.contains(key)) selectedKeys - key else selectedKeys + key
        if (selectedKeys.isEmpty()) selectionMode = false
    }

    val visibleAlbums = remember(albumsWithStats, showHidden, sortOption) {
        sortAlbums(albumsWithStats.filter { showHidden || !it.isHidden }, sortOption)
    }
    val normalFolders = remember(deviceFolders, showHidden, sortOption) {
        sortDeviceFolders(
            deviceFolders.filter { !it.isSystemHidden && (showHidden || !it.isHidden) },
            sortOption
        )
    }
    val systemHiddenFolders = remember(deviceFolders, sortOption) {
        sortDeviceFolders(deviceFolders.filter { it.isSystemHidden }, sortOption)
    }
    val selectableByKey = remember(visibleAlbums, normalFolders) {
        val map = mutableMapOf<String, SelectableAlbum>()
        visibleAlbums.forEach {
            val key = "album_${it.album.id}"
            map[key] = SelectableAlbum(key, it.album.id, null, it.isHidden)
        }
        normalFolders.forEach {
            val key = "folder_${it.name}"
            map[key] = SelectableAlbum(key, null, it.name, it.isHidden)
        }
        map
    }
    val selectedEntries = selectedKeys.mapNotNull { selectableByKey[it] }
    val canDeleteSelection = selectedEntries.isNotEmpty() && selectedEntries.all { it.albumId != null }
    val allSelectedHidden = selectedEntries.isNotEmpty() && selectedEntries.all { it.isHidden }

    androidx.compose.runtime.LaunchedEffect(sortOption) {
        gridState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                CenterAlignedTopAppBar(
                    title = { Text("${selectedKeys.size} selecionado${if (selectedKeys.size == 1) "" else "s"}") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar seleção")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("Álbuns") },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                        }
                        FilledTonalIconButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                // secondaryContainer é o tom "discreto" da paleta dinâmica do
                                // Material You (a mesma extraída do papel de parede, porém com
                                // menos saturação que a cor primária). A leve transparência deixa
                                // ainda mais claro, no espírito do botão de ordenar do Google Keep,
                                // sem perder a cor do tema do usuário.
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Filled.Sort, contentDescription = "Ordenar por")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            AlbumSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        viewModel.setAlbumSortOption(option)
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Criar álbum")
                }
            }
        },
        bottomBar = {
            if (selectionMode && selectedKeys.isNotEmpty()) {
                AlbumSelectionActionBar(
                    selectedCount = selectedKeys.size,
                    onClearSelection = { exitSelection() },
                    onToggleHidden = {
                        val targetHidden = !allSelectedHidden
                        selectedEntries.forEach { entry ->
                            if (entry.albumId != null) {
                                viewModel.setAlbumHidden(entry.albumId, targetHidden)
                            } else if (entry.folderName != null) {
                                viewModel.setFolderHidden(entry.folderName, targetHidden)
                            }
                        }
                        exitSelection()
                    },
                    hideLabel = if (allSelectedHidden) "Mostrar" else "Ocultar",
                    hideIcon = !allSelectedHidden,
                    onDelete = if (canDeleteSelection) {
                        { showDeleteConfirm = true }
                    } else null
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (visibleAlbums.isEmpty() && normalFolders.isEmpty() && systemHiddenFolders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum álbum ainda. Toque em + para criar.")
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    if (visibleAlbums.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Meus álbuns",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(visibleAlbums, key = { "album_${it.album.id}" }) { stats ->
                            val key = "album_${stats.album.id}"
                            AlbumCard(
                                title = stats.album.name,
                                coverUri = stats.coverUri,
                                count = stats.count,
                                totalSizeBytes = stats.totalSizeBytes,
                                isSecret = stats.album.isSecret,
                                isHidden = stats.isHidden,
                                icon = Icons.Filled.PhotoAlbum,
                                selectionMode = selectionMode,
                                isSelected = selectedKeys.contains(key),
                                onClick = {
                                    if (selectionMode) toggleSelection(key)
                                    else onOpenAlbum(stats.album.id, stats.album.isSecret)
                                },
                                onLongClick = {
                                    selectionMode = true
                                    toggleSelection(key)
                                }
                            )
                        }
                    }
                    if (normalFolders.isNotEmpty()) {
                        items(normalFolders, key = { "folder_${it.name}" }) { folder ->
                            val key = "folder_${folder.name}"
                            AlbumCard(
                                title = folder.name,
                                coverUri = folder.coverUri,
                                count = folder.count,
                                totalSizeBytes = folder.totalSizeBytes,
                                isSecret = false,
                                isHidden = folder.isHidden,
                                icon = Icons.Filled.Folder,
                                selectionMode = selectionMode,
                                isSelected = selectedKeys.contains(key),
                                onClick = {
                                    if (selectionMode) toggleSelection(key)
                                    else onOpenDeviceFolder(folder.name)
                                },
                                onLongClick = {
                                    selectionMode = true
                                    toggleSelection(key)
                                }
                            )
                        }
                    }
                    if (systemHiddenFolders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Pastas ocultas do sistema",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            )
                        }
                        items(systemHiddenFolders, key = { "safFolder_${it.name}" }) { folder ->
                            AlbumCard(
                                title = folder.name,
                                coverUri = folder.coverUri,
                                count = folder.count,
                                totalSizeBytes = folder.totalSizeBytes,
                                isSecret = false,
                                isHidden = false,
                                icon = Icons.Filled.FolderOff,
                                selectionMode = false,
                                isSelected = false,
                                onClick = { onOpenDeviceFolder(folder.name) },
                                onLongClick = {}
                            )
                        }
                    }
                }
                SimpleVerticalScrollbar(state = gridState)
            }
        }
    }

    if (showCreateDialog) {
        CreateAlbumDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, isSecret ->
                showCreateDialog = false
                viewModel.createAlbum(name, isSecret) { id -> onOpenAlbum(id, isSecret) }
            }
        )
    }

    if (showDeleteConfirm) {
        val count = selectedEntries.count { it.albumId != null }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (count == 1) "Excluir álbum" else "Excluir álbuns") },
            text = {
                Text(
                    if (count == 1) "Tem certeza que deseja excluir este álbum? As fotos não serão apagadas do dispositivo."
                    else "Tem certeza que deseja excluir estes $count álbuns? As fotos não serão apagadas do dispositivo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedEntries.forEach { entry ->
                        entry.albumId?.let { viewModel.deleteAlbum(it) }
                    }
                    showDeleteConfirm = false
                    exitSelection()
                }) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    title: String,
    coverUri: String?,
    count: Int,
    totalSizeBytes: Long,
    isSecret: Boolean,
    isHidden: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSecret) Icons.Filled.Lock else icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (isSecret) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Secreto", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            if (isHidden) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp)
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = "Oculto", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selecionado",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp, bottom = 3.dp)
        )
        Text(
            text = "$count ${if (count == 1) "foto" else "fotos"} · ${FileUtils.formatSize(totalSizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
