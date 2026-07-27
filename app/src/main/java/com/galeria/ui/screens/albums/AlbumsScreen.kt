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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.galeria.ui.AlbumSortOption
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.SimpleVerticalScrollbar
import com.galeria.ui.sortAlbums
import com.galeria.ui.sortDeviceFolders
import com.galeria.util.FileUtils

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
    var showCreateDialog by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(AlbumSortOption.RECENTE) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Álbuns") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Ordenar por")
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        AlbumSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortOption = option
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Criar álbum")
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
                            AlbumCard(
                                title = stats.album.name,
                                coverUri = stats.coverUri,
                                count = stats.count,
                                totalSizeBytes = stats.totalSizeBytes,
                                isSecret = stats.album.isSecret,
                                isHidden = stats.isHidden,
                                icon = Icons.Filled.PhotoAlbum,
                                onClick = { onOpenAlbum(stats.album.id, stats.album.isSecret) },
                                onToggleHidden = { viewModel.setAlbumHidden(stats.album.id, !stats.isHidden) }
                            )
                        }
                    }
                    if (normalFolders.isNotEmpty()) {
                        items(normalFolders, key = { "folder_${it.name}" }) { folder ->
                            AlbumCard(
                                title = folder.name,
                                coverUri = folder.coverUri,
                                count = folder.count,
                                totalSizeBytes = folder.totalSizeBytes,
                                isSecret = false,
                                isHidden = folder.isHidden,
                                icon = Icons.Filled.Folder,
                                onClick = { onOpenDeviceFolder(folder.name) },
                                onToggleHidden = { viewModel.setFolderHidden(folder.name, !folder.isHidden) }
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
                                onClick = { onOpenDeviceFolder(folder.name) },
                                onToggleHidden = {}
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
    onClick: () -> Unit,
    onToggleHidden: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onToggleHidden)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
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
