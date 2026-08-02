package com.galeria.ui

import android.app.Application
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeria.GaleriaApplication
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.Photo
import com.galeria.data.model.SafFolderEntity
import com.galeria.util.DateUtils
import com.galeria.util.SafUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

enum class PhotoSortOption(val label: String) {
    RECENTE("Mais recentes primeiro"),
    ANTIGA("Mais antigas primeiro"),
    MAIOR_TAMANHO("Maior tamanho primeiro"),
    MENOR_TAMANHO("Menor tamanho primeiro")
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

    // Quantidade de colunas da grade de fotos/álbuns, configurável em Ajustes. 3 é o valor
    // que o app sempre usou (fixo no código antes), mantido como padrão pra não mudar a
    // aparência de quem já usa o app sem querer.
    private val _photoGridColumns = MutableStateFlow(prefs.getInt("photo_grid_columns", 3).coerceIn(2, 5))
    val photoGridColumns: StateFlow<Int> = _photoGridColumns

    fun setPhotoGridColumns(count: Int) {
        val coerced = count.coerceIn(2, 5)
        _photoGridColumns.value = coerced
        prefs.edit().putInt("photo_grid_columns", coerced).apply()
    }

    private val _albumSortOption = MutableStateFlow(
        try {
            AlbumSortOption.valueOf(prefs.getString("album_sort_option", AlbumSortOption.RECENTE.name) ?: AlbumSortOption.RECENTE.name)
        } catch (_: IllegalArgumentException) {
            AlbumSortOption.RECENTE
        }
    )
    val albumSortOption: StateFlow<AlbumSortOption> = _albumSortOption

    fun setAlbumSortOption(option: AlbumSortOption) {
        _albumSortOption.value = option
        prefs.edit().putString("album_sort_option", option.name).apply()
    }

    // Ordem de exibição das fotos, agora por álbum/pasta em vez de uma única preferência
    // global — cada tela (álbum criado ou pasta do dispositivo) passa sua própria scopeKey
    // (ex.: "album_42" ou "folder_Camera") e guarda/lê a escolha só daquele escopo.
    fun getPhotoSortOptionFor(scopeKey: String): PhotoSortOption {
        val stored = prefs.getString("photo_sort_option_$scopeKey", null) ?: return PhotoSortOption.RECENTE
        return try {
            PhotoSortOption.valueOf(stored)
        } catch (_: IllegalArgumentException) {
            PhotoSortOption.RECENTE
        }
    }

    fun setPhotoSortOptionFor(scopeKey: String, option: PhotoSortOption) {
        prefs.edit().putString("photo_sort_option_$scopeKey", option.name).apply()
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

    private fun groupPhotosByMonth(photos: List<Photo>): List<MonthGroup> =
        groupPhotosByMonthUtil(photos)

    // Estado transitório (não persiste) da opacidade da barra de baixo: fica transparente
    // enquanto o usuário rola pra baixo dentro da grade (Fotos/Álbuns/Favoritos) e volta a
    // ficar opaca ao rolar de volta pra cima ou ao chegar no topo. Quem atualiza isso é o
    // ObserveGridScrollForBottomBar, chamado de dentro de cada uma dessas 3 telas.
    private val _bottomBarOpaque = MutableStateFlow(true)
    val bottomBarOpaque: StateFlow<Boolean> = _bottomBarOpaque

    fun setBottomBarOpaque(opaque: Boolean) {
        if (_bottomBarOpaque.value != opaque) _bottomBarOpaque.value = opaque
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

    fun setFavorites(uris: Collection<String>, value: Boolean) {
        viewModelScope.launch {
            uris.forEach { app.favoriteRepository.setFavorite(it, value) }
        }
    }

    fun addPhotosToAlbum(albumId: Long, uris: Collection<String>) {
        viewModelScope.launch {
            uris.forEach { app.albumRepository.addPhoto(albumId, it) }
        }
    }

    fun removePhotosFromAlbum(albumId: Long, uris: Collection<String>) {
        viewModelScope.launch {
            uris.forEach { app.albumRepository.removePhoto(albumId, it) }
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

    // --- Limpar cache de miniaturas (Coil) ---
    //
    // Não apaga nenhuma foto/vídeo real -- só o cache de miniaturas que o Coil mantém em
    // memória e em disco pra carregar a grade mais rápido. Ele se reconstrói sozinho conforme
    // as telas são reabertas, então é seguro limpar a qualquer momento.
    fun clearImageCache() {
        val loader = coil.Coil.imageLoader(getApplication())
        loader.memoryCache?.clear()
        viewModelScope.launch(Dispatchers.IO) {
            loader.diskCache?.clear()
        }
    }

    suspend fun isBiometricEnabled(): Boolean = app.securityRepository.isBiometricEnabled()

    suspend fun setBiometricEnabled(enabled: Boolean) =
        app.securityRepository.setBiometricEnabled(enabled)

    // --- "Mover para pasta" (mover o arquivo de verdade no disco, tipo o Google Fotos) ---
    //
    // Isso é bem diferente do "Mover para álbum" que já existe: aqui a gente muda de fato o
    // caminho físico do arquivo (RELATIVE_PATH no MediaStore), então some da pasta de origem
    // de verdade — não é só uma marcação virtual como os álbuns do app.

    /**
     * Descobre o RELATIVE_PATH a ser usado. Se já existe uma pasta do dispositivo com esse
     * nome, reaproveita o caminho exato dela (assim o arquivo se junta à pasta certa, seja
     * ela DCIM/Camera, Pictures/Screenshots etc.). Se for uma pasta nova, cria embaixo de
     * Pictures (fotos) ou Movies (vídeos), que é onde o Android permite criar pastas novas
     * pelo MediaStore sem pedir mais permissões.
     */
    fun resolveTargetRelativePath(targetFolderName: String, isVideo: Boolean): String {
        val existingSample = _allPhotos.value.firstOrNull { it.bucketName == targetFolderName }
        if (existingSample != null) {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val withoutRoot = existingSample.path.removePrefix(root).trimStart('/')
            val dir = withoutRoot.substringBeforeLast('/', missingDelimiterValue = "")
            if (dir.isNotBlank()) return "$dir/"
        }
        val base = if (isVideo) "Movies" else "Pictures"
        return "$base/$targetFolderName/"
    }

    /**
     * No Android 11+ (API 30+), o app precisa pedir permissão de escrita pro lote inteiro de
     * uma vez antes de tentar mudar o caminho de qualquer item que ele não seja o "dono"
     * (ex.: fotos tiradas pela câmera do sistema). Retorna null em versões mais antigas ou se
     * o app já tiver permissão de sobra.
     */
    fun buildMoveWriteRequest(uriStrings: List<String>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = uriStrings.map { Uri.parse(it) }
        return try {
            MediaStore.createWriteRequest(getApplication<Application>().contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Aplica a mudança de pasta em cada item (assumindo que a permissão de escrita, quando
     * necessária, já foi concedida). Retorna a quantidade de itens que falharam — 0 significa
     * que deu tudo certo.
     */
    suspend fun applyMoveToFolder(uriStrings: List<String>, targetFolderName: String): Int =
        withContext(Dispatchers.IO) {
            var failures = 0
            val resolver = getApplication<Application>().contentResolver
            uriStrings.forEach { uriString ->
                val photo = getPhotoByUri(uriString)
                val relativePath = resolveTargetRelativePath(targetFolderName, photo?.isVideo == true)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                try {
                    val updated = resolver.update(Uri.parse(uriString), values, null, null)
                    if (updated <= 0) failures++
                } catch (_: Exception) {
                    failures++
                }
            }
            loadPhotos()
            failures
        }
}

fun groupPhotosByMonthUtil(photos: List<Photo>, descending: Boolean = true): List<MonthGroup> {
    val groups = photos.groupBy { DateUtils.monthYearKey(it.dateTakenMillis) }
        .map { (key, list) ->
            val sortedList = if (descending) list.sortedByDescending { it.dateTakenMillis } else list.sortedBy { it.dateTakenMillis }
            MonthGroup(key, DateUtils.monthYearLabel(sortedList.first().dateTakenMillis), sortedList)
        }
    return if (descending) {
        groups.sortedByDescending { it.photos.first().dateTakenMillis }
    } else {
        groups.sortedBy { it.photos.first().dateTakenMillis }
    }
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

fun sortPhotos(list: List<Photo>, option: PhotoSortOption): List<Photo> =
    when (option) {
        PhotoSortOption.RECENTE -> list.sortedByDescending { it.dateTakenMillis }
        PhotoSortOption.ANTIGA -> list.sortedBy { it.dateTakenMillis }
        PhotoSortOption.MAIOR_TAMANHO -> list.sortedByDescending { it.sizeBytes }
        PhotoSortOption.MENOR_TAMANHO -> list.sortedBy { it.sizeBytes }
    }
