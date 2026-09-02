package com.galeria.ui.screens.picker

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.galeria.data.model.Photo
import com.galeria.ui.AlbumWithStats
import com.galeria.ui.DeviceFolder
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.PhotoGridItem

/**
 * Tela mostrada quando o Galeria é aberto por OUTRO app (ex.: escolher uma imagem para
 * upload num site, anexar num e-mail, etc.) via ACTION_GET_CONTENT ou ACTION_PICK.
 *
 * Igual ao seletor do Gallery do Google: primeiro mostra "Todas as fotos" + os álbuns/pastas,
 * e só ao entrar em um deles é que aparece a grade de fotos pra escolher. Álbuns/pastas
 * ocultos ou secretos do app nunca aparecem aqui, mesmo que a pessoa tenha ativado
 * "mostrar ocultos" nas configurações -- isso é proposital: o objetivo de marcar algo como
 * oculto/secreto é justamente não deixá-lo visível pra outros apps.
 */
private sealed class PickerDestination {
    object Root : PickerDestination()
    object AllPhotos : PickerDestination()
    data class Folder(val name: String) : PickerDestination()
    data class Album(val id: Long, val name: String) : PickerDestination()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPickerScreen(
    viewModel: GalleryViewModel,
    allowMultiple: Boolean,
    filter: (Photo) -> Boolean,
    onPick: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    var destination by remember { mutableStateOf<PickerDestination>(PickerDestination.Root) }
    BackHandler {
        if (destination == PickerDestination.Root) onCancel() else destination = PickerDestination.Root
    }

    val allPhotos by viewModel.allPhotos.collectAsState()
    val gridColumns by viewModel.photoGridColumns.collectAsState()
    val favorites by viewModel.favoriteUris.collectAsState()
    val deviceFoldersRaw by viewModel.deviceFolders.collectAsState()
    val albumsWithStatsRaw by viewModel.albumsWithStats.collectAsState()

    val filteredAllPhotos = remember(allPhotos, filter) { allPhotos.filter(filter) }

    val pickerFolders = remember(deviceFoldersRaw, filter) {
        deviceFoldersRaw
            .filter { !it.isHidden && !it.isSystemHidden }
            .map { it to it.photos.filter(filter) }
            .filter { (_, photos) -> photos.isNotEmpty() }
    }

    val pickerAlbums = remember(albumsWithStatsRaw, allPhotos, filter) {
        val byUri = allPhotos.associateBy { it.uri.toString() }
        albumsWithStatsRaw
            .filter { !it.isHidden && !it.album.isSecret }
            .map { stats -> stats to stats.photoUris.mapNotNull { byUri[it] }.filter(filter) }
            .filter { (_, photos) -> photos.isNotEmpty() }
    }

    // Lista (não Set) só pra manter a ordem em que a pessoa tocou nas fotos -- a seleção é
    // global, então dá pra escolher fotos de mais de um álbum/pasta antes de "Concluir".
    val selected = remember { mutableStateListOf<String>() }

    fun toggle(photo: Photo) {
        val uriStr = photo.uri.toString()
        if (selected.contains(uriStr)) selected.remove(uriStr) else selected.add(uriStr)
    }

    val title = when (val d = destination) {
        PickerDestination.Root -> if (allowMultiple) "Selecionar imagens" else "Selecionar imagem"
        PickerDestination.AllPhotos -> "Todas as fotos"
        is PickerDestination.Folder -> d.name
        is PickerDestination.Album -> d.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (destination == PickerDestination.Root) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                        }
                    } else {
                        IconButton(onClick = { destination = PickerDestination.Root }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                        }
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
        when (val d = destination) {
            PickerDestination.Root -> PickerRootGrid(
                modifier = Modifier.padding(padding),
                gridColumns = gridColumns,
                allPhotosCount = filteredAllPhotos.size,
                allPhotosCover = filteredAllPhotos.firstOrNull()?.uri?.toString(),
                albums = pickerAlbums,
                folders = pickerFolders,
                onOpenAllPhotos = { destination = PickerDestination.AllPhotos },
                onOpenAlbum = { id, name -> destination = PickerDestination.Album(id, name) },
                onOpenFolder = { name -> destination = PickerDestination.Folder(name) }
            )
            else -> {
                val photos = when (d) {
                    PickerDestination.AllPhotos -> filteredAllPhotos
                    is PickerDestination.Folder -> pickerFolders.firstOrNull { it.first.name == d.name }?.second.orEmpty()
                    is PickerDestination.Album -> pickerAlbums.firstOrNull { it.first.album.id == d.id }?.second.orEmpty()
                    PickerDestination.Root -> emptyList()
                }
                PickerPhotoGrid(
                    modifier = Modifier.padding(padding),
                    photos = photos,
                    gridColumns = gridColumns,
                    favorites = favorites,
                    allowMultiple = allowMultiple,
                    selected = selected,
                    onToggle = { toggle(it) },
                    onPickSingle = { onPick(listOf(it.uri)) }
                )
            }
        }
    }
}

@Composable
private fun PickerRootGrid(
    modifier: Modifier,
    gridColumns: Int,
    allPhotosCount: Int,
    allPhotosCover: String?,
    albums: List<Pair<AlbumWithStats, List<Photo>>>,
    folders: List<Pair<DeviceFolder, List<Photo>>>,
    onOpenAllPhotos: () -> Unit,
    onOpenAlbum: (Long, String) -> Unit,
    onOpenFolder: (String) -> Unit
) {
    if (allPhotosCount == 0 && albums.isEmpty() && folders.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma imagem encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            PickerDestinationCard(
                title = "Todas as fotos",
                count = allPhotosCount,
                coverUri = allPhotosCover,
                icon = Icons.Filled.PhotoLibrary,
                onClick = onOpenAllPhotos
            )
        }
        items(albums, key = { "album_${it.first.album.id}" }) { (stats, photos) ->
            PickerDestinationCard(
                title = stats.album.name,
                count = photos.size,
                coverUri = photos.firstOrNull()?.uri?.toString(),
                icon = Icons.Filled.PhotoAlbum,
                onClick = { onOpenAlbum(stats.album.id, stats.album.name) }
            )
        }
        items(folders, key = { "folder_${it.first.name}" }) { (folder, photos) ->
            PickerDestinationCard(
                title = folder.name,
                count = photos.size,
                coverUri = photos.firstOrNull()?.uri?.toString(),
                icon = Icons.Filled.Folder,
                onClick = { onOpenFolder(folder.name) }
            )
        }
    }
}

@Composable
private fun PickerDestinationCard(
    title: String,
    count: Int,
    coverUri: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Text(
            text = "$count ${if (count == 1) "item" else "itens"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PickerPhotoGrid(
    modifier: Modifier,
    photos: List<Photo>,
    gridColumns: Int,
    favorites: Set<String>,
    allowMultiple: Boolean,
    selected: List<String>,
    onToggle: (Photo) -> Unit,
    onPickSingle: (Photo) -> Unit
) {
    if (photos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma imagem encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = 2.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            val uriStr = photo.uri.toString()
            PhotoGridItem(
                photo = photo,
                isFavorite = favorites.contains(uriStr),
                isSelected = selected.contains(uriStr),
                selectionMode = allowMultiple,
                onClick = {
                    if (allowMultiple) onToggle(photo) else onPickSingle(photo)
                },
                onLongClick = {
                    if (allowMultiple && !selected.contains(uriStr)) onToggle(photo)
                }
            )
        }
    }
}
