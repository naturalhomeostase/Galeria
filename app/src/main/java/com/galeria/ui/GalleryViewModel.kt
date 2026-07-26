package com.galeria.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeria.GaleriaApplication
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.Photo
import com.galeria.data.model.SafFolderEntity
import com.galeria.util.DateUtils
import com.galeria.util.SafUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonthGroup(val key: String, val label: String, val photos: List<Photo>)

data class AlbumWithStats(
    val album: AlbumEntity,
    val photoUris: List<String>,
    val count: Int,
    val totalSizeBytes: Long,
    val coverUri: String?,
    val isHidden: Boolean
)

data class DeviceFolder(
    val name: String,
    val photos: List<Photo>,
    val isHidden: Boolean = false,
    val isSystemHidden: Boolean = false
) {
    val count: Int get() = photos.size
    val totalSizeBytes: Long get() = photos.sumOf { it.sizeBytes }
    val coverUri: String? get() = photos.firstOrNull()?.uri?.toString()
    val lastModifiedAt: Long get() = photos.maxOfOrNull { it.dateTakenMillis } ?: 0L
}

enum class AlbumSortOption(val label: String) {
    RECENTE("Recente"),
    NOME_AZ("Nome (A-Z)"),
    NOME_ZA("Nome (Z-A)"),
    DATA_MODIFICACAO("Data de modificação"),
    TAMANHO("Tamanho")
}

enum class ThemeMode(val label: String) {
    SISTEMA("Automático (sistema)"),
    CLARO("Claro"),
    ESCURO("Escuro")
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GaleriaApplication
    private val prefs: SharedPreferences =
        application.getSharedPreferences("galeria_prefs", Context.MODE_PRIVATE)

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos: StateFlow<List<Photo>> = _allPhotos

    private val _isLoadingPhotos = MutableStateFlow(false)
    val isLoadingPhotos: StateFlow<Boolean> = _isLoadingPhotos

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    private val _trashedPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val trashedPhotos: StateFlow<List<Photo>> = _trashedPhotos

    private val _showHiddenAlbums = MutableStateFlow(prefs.getBoolean("show_hidden_albums", false))
    val showHiddenAlbums: StateFlow<Boolean> = _showHiddenAlbums

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SISTEMA.name) ?: ThemeMode.SISTEMA.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SISTEMA
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    val favoriteUris: StateFlow<Set<String>> = app.favoriteRepository.getFavoriteUris()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val hiddenFolderNames: StateFlow<Set<String>> = app.settingsRepository.getHiddenFolderNames()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val safFolders: StateFlow<List<SafFolderEntity>> = app.settingsRepository.getSafFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val safFolderNames: StateFlow<Set<String>> = safFolders
        .map { list -> list.map { it.displayName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val monthGroups: StateFlow<List<MonthGroup>> = combine(
        _allPhotos, hiddenFolderNames, _showHiddenAlbums
    ) { photos, hidden, showHidden ->
        val visible = if (showHidden) photos else photos.filter { it.bucketName !in hidden }
        groupPhotosByMonth(visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceFolders: StateFlow<List<DeviceFolder>> = combine(
        _allPhotos, hiddenFolderNames, safFolderNames
    ) { photos, hidden, safNames ->
        photos.groupBy { it.bucketName }
            .filter { it.key.isNotBlank() }
            .map { (name, list) ->
                DeviceFolder(
                    name = name,
                    photos = list,
                    isHidden = hidden.contains(name),
                    isSystemHidden = safNames.contains(name)
                )
            }
            .sortedByDescending { it.lastModifiedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumsWithStats: StateFlow<List<AlbumWithStats>> = combine(
        app.albumRepository.getAlbums(),
        app.albumRepository.getAllCrossRefs(),
        _allPhotos
    ) { albums, crossRefs, photos ->
        val photosByUri = photos.associateBy { it.uri.toString() }
        val crossByAlbum = crossRefs.groupBy { it.albumId }
        albums.map { album ->
            val refs = crossByAlbum[album.id].orEmpty().sortedByDescending { it.addedAt }
            val uris = refs.map { it.photoUri }
            val matchedPhotos = uris.mapNotNull { photosByUri[it] }
            AlbumWithStats(
                album = album,
                photoUris = uris,
                count = uris.size,
                totalSizeBytes = matchedPhotos.sumOf { it.sizeBytes },
                coverUri = uris.firstOrNull(),
                isHidden = album.isHidden
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun groupPhotosByMonth(photos: List<Photo>): List<MonthGroup> {
        return photos.groupBy { DateUtils.monthYearKey(it.dateTakenMillis) }
            .map { (key, list) ->
                MonthGroup(key, DateUtils.monthYearLabel(list.first().dateTakenMillis), list)
            }
            .sortedByDescending { it.photos.first().dateTakenMillis }
    }

    fun onPermissionGranted() {
        _hasPermission.value = true
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _isLoadingPhotos.value = true
            val mediaPhotos = app.mediaStoreRepository.getAllPhotos()
            val currentSafFolders = app.settingsRepository.getSafFolders().first()
            val safPhotos = currentSafFolders.flatMap { folder ->
                SafUtils.loadImagesFromTree(getApplication(), Uri.parse(folder.treeUri), folder.displayName)
            }
            _allPhotos.value = mediaPhotos + safPhotos
            _isLoadingPhotos.value = false
            loadTrash()
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            _trashedPhotos.value = app.mediaStoreRepository.getTrashedPhotos()
        }
    }

    fun restoreFromTrash(uriString: String) {
        viewModelScope.launch {
            app.mediaStoreRepository.restoreFromTrash(Uri.parse(uriString))
            loadTrash()
            loadPhotos()
        }
    }

    fun toggleFavorite(photoUri: String) {
        viewModelScope.launch {
            app.favoriteRepository.toggle(photoUri)
        }
    }

    fun createAlbum(name: String, isSecret: Boolean, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = app.albumRepository.createAlbum(name, isSecret)
            onCreated(id)
        }
    }

    fun addPhotoToAlbum(albumId: Long, photoUri: String) {
        viewModelScope.launch { app.albumRepository.addPhoto(albumId, photoUri) }
    }

    fun removePhotoFromAlbum(albumId: Long, photoUri: String) {
        viewModelScope.launch { app.albumRepository.removePhoto(albumId, photoUri) }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch { app.albumRepository.deleteAlbum(albumId) }
    }

    fun setAlbumHidden(albumId: Long, hidden: Boolean) {
        viewModelScope.launch { app.albumRepository.setHidden(albumId, hidden) }
    }

    fun setFolderHidden(bucketName: String, hidden: Boolean) {
        viewModelScope.launch {
            if (hidden) app.settingsRepository.hideFolder(bucketName)
            else app.settingsRepository.unhideFolder(bucketName)
        }
    }

    fun setShowHiddenAlbums(show: Boolean) {
        _showHiddenAlbums.value = show
        prefs.edit().putBoolean("show_hidden_albums", show).apply()
    }

    fun addSafFolder(treeUriString: String, displayName: String) {
        viewModelScope.launch {
            app.settingsRepository.addSafFolder(treeUriString, displayName)
            loadPhotos()
        }
    }

    fun removeSafFolder(id: Long) {
        viewModelScope.launch {
            app.settingsRepository.removeSafFolder(id)
            loadPhotos()
        }
    }

    fun getPhotoByUri(uriString: String): Photo? =
        _allPhotos.value.firstOrNull { it.uri.toString() == uriString }

    fun resolvePhotos(uris: List<String>): List<Photo> {
        val byUri = _allPhotos.value.associateBy { it.uri.toString() }
        return uris.mapNotNull { byUri[it] }
    }

    suspend fun hasSecretPassword(): Boolean = app.securityRepository.hasPassword()

    suspend fun setSecretPassword(password: String) = app.securityRepository.setPassword(password)

    suspend fun verifySecretPassword(password: String): Boolean =
        app.securityRepository.verifyPassword(password)

    suspend fun isBiometricEnabled(): Boolean = app.securityRepository.isBiometricEnabled()

    suspend fun setBiometricEnabled(enabled: Boolean) =
        app.securityRepository.setBiometricEnabled(enabled)
}

fun sortAlbums(list: List<AlbumWithStats>, option: AlbumSortOption): List<AlbumWithStats> =
    when (option) {
        AlbumSortOption.RECENTE -> list.sortedByDescending { it.album.createdAt }
        AlbumSortOption.NOME_AZ -> list.sortedBy { it.album.name.lowercase() }
        AlbumSortOption.NOME_ZA -> list.sortedByDescending { it.album.name.lowercase() }
        AlbumSortOption.DATA_MODIFICACAO -> list.sortedByDescending { it.album.lastModifiedAt }
        AlbumSortOption.TAMANHO -> list.sortedByDescending { it.totalSizeBytes }
    }

fun sortDeviceFolders(list: List<DeviceFolder>, option: AlbumSortOption): List<DeviceFolder> =
    when (option) {
        AlbumSortOption.RECENTE -> list.sortedByDescending { it.lastModifiedAt }
        AlbumSortOption.NOME_AZ -> list.sortedBy { it.name.lowercase() }
        AlbumSortOption.NOME_ZA -> list.sortedByDescending { it.name.lowercase() }
        AlbumSortOption.DATA_MODIFICACAO -> list.sortedByDescending { it.lastModifiedAt }
        AlbumSortOption.TAMANHO -> list.sortedByDescending { it.totalSizeBytes }
    }
