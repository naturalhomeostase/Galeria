package com.galeria.ui

import android.app.Application
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.content.ContentResolver
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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

    // Fotos/Álbuns/Favoritos têm seu próprio "menu de seleção" ancorado embaixo (Copiar,
    // Mover, Excluir...). Como a barra de navegação agora flutua por cima do conteúdo pra dar
    // o efeito de transparência, ela ficava sobrepondo e bloqueando o toque nesse menu de
    // seleção -- por isso ele precisa sumir enquanto uma seleção estiver ativa.
    private val _selectionModeActive = MutableStateFlow(false)
    val selectionModeActive: StateFlow<Boolean> = _selectionModeActive

    fun setSelectionModeActive(active: Boolean) {
        if (_selectionModeActive.value != active) _selectionModeActive.value = active
    }

    // Guarda a posição de rolagem de cada grade (Fotos/Álbuns/Favoritos) pra restaurar depois
    // de voltar de uma foto ou álbum -- não precisa ser reativo (StateFlow), só lido uma vez
    // na hora de criar o LazyGridState e escrito continuamente enquanto rola.
    private val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun setScrollPosition(scopeKey: String, index: Int, offset: Int) {
        scrollPositions[scopeKey] = index to offset
    }

    fun getScrollPosition(scopeKey: String): Pair<Int, Int> = scrollPositions[scopeKey] ?: (0 to 0)

    // Sinal "algo mudou no MediaStore" (screenshot, foto tirada pela câmera, arquivo apagado
    // por outro app etc.) -- não carrega os dados aqui dentro, só avisa que precisa recarregar.
    // O debounce(400) evita disparar loadPhotos() várias vezes seguidas quando o sistema manda
    // uma rajada de notificações pra uma única operação (uma captura de tela, por exemplo,
    // costuma gerar mais de um onChange: criação do registro + atualização depois que o
    // arquivo termina de ser escrito).
    private val mediaChangeSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var mediaObserver: ContentObserver? = null

    init {
        viewModelScope.launch {
            mediaChangeSignal.debounce(400).collect {
                loadPhotos()
            }
        }
    }

    // Observa a galeria do sistema (MediaStore) pra saber, em tempo real, quando uma foto ou
    // vídeo é adicionado, alterado ou removido por fora do nosso app -- é o que faz, por
    // exemplo, um print de tela aparecer na grade na hora, sem precisar reabrir o app.
    // Chamado só depois da permissão de mídia concedida (registrar antes disso não tem
    // utilidade, já que qualquer loadPhotos() disparado falharia por falta de permissão).
    private fun registerMediaObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                mediaChangeSignal.tryEmit(Unit)
            }
        }
        mediaObserver = observer
        val resolver = app.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    override fun onCleared() {
        super.onCleared()
        mediaObserver?.let { app.contentResolver.unregisterContentObserver(it) }
        mediaObserver = null
    }

    fun onPermissionGranted() {
        _hasPermission.value = true
        loadPhotos()
        registerMediaObserver()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _isLoadingPhotos.value = true
            val mediaPhotos = app.mediaStoreRepository.getAllPhotos()
            val currentSafFolders = app.settingsRepository.getSafFolders().first()
            // Antes isso era um flatMap sequencial: cada pasta oculta só começava a ser lida
            // depois que a anterior terminava por completo. Com várias pastas SAF cadastradas,
            // os tempos se somavam um atrás do outro. Rodando em paralelo, o tempo total passa
            // a ser o da pasta mais lenta, não a soma de todas.
            val safPhotos = coroutineScope {
                currentSafFolders
                    .map { folder ->
                        async { SafUtils.loadImagesFromTree(getApplication(), Uri.parse(folder.treeUri), folder.displayName) }
                    }
                    .awaitAll()
                    .flatten()
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

    val trashedAlbums: StateFlow<List<AlbumEntity>> = app.albumRepository.getTrashedAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // "Excluir álbum" no dia a dia move pra lixeira (reversível) em vez de apagar na hora --
    // deleteAlbum (abaixo) continua existindo, mas agora só é chamado a partir da lixeira,
    // como exclusão definitiva mesmo.
    fun moveAlbumToTrash(albumId: Long) {
        viewModelScope.launch { app.albumRepository.moveToTrash(albumId) }
    }

    fun restoreAlbumFromTrash(albumId: Long) {
        viewModelScope.launch { app.albumRepository.restoreFromTrash(albumId) }
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
    // O MediaStore só permite que itens de imagem/vídeo fiquem dentro de um conjunto fixo de
    // pastas de primeiro nível -- tentar inserir em qualquer outra é sempre rejeitado pelo
    // sistema, não é uma questão de permissão. Pastas como "WhatsApp Images" ou "Telegram
    // Images" costumam ficar direto na raiz do armazenamento (fora de DCIM/Pictures), então
    // reaproveitar o caminho exato delas não funciona -- por isso mover para uma pasta "nova"
    // sempre dava certo (cai em Pictures/Nome) mas mover para certas pastas existentes falhava.
    private val ALLOWED_IMAGE_TOP_DIRS = setOf("DCIM", "Pictures")
    private val ALLOWED_VIDEO_TOP_DIRS = setOf("DCIM", "Pictures", "Movies")

    fun resolveTargetRelativePath(targetFolderName: String, isVideo: Boolean): String {
        val allowedTopDirs = if (isVideo) ALLOWED_VIDEO_TOP_DIRS else ALLOWED_IMAGE_TOP_DIRS
        val base = if (isVideo) "Movies" else "Pictures"

        val existingSample = _allPhotos.value.firstOrNull { it.bucketName == targetFolderName }
        if (existingSample != null) {
            val root = Environment.getExternalStorageDirectory().absolutePath
            val withoutRoot = existingSample.path.removePrefix(root).trimStart('/')
            val dir = withoutRoot.substringBeforeLast('/', missingDelimiterValue = "")
            val topDir = dir.substringBefore('/', missingDelimiterValue = dir)
            // Só reaproveita o caminho original se ele realmente for permitido pro tipo de
            // mídia. Também rejeita qualquer coisa que pareça um caminho de pasta SAF (content://)
            // em vez de um caminho de arquivo de verdade.
            if (dir.isNotBlank() && topDir in allowedTopDirs && !dir.contains("://")) {
                return "$dir/"
            }
        }
        return "$base/$targetFolderName/"
    }

    /**
     * Renomeia uma pasta do dispositivo -- na prática, "move" cada arquivo dela pro mesmo
     * lugar só que com o nome final trocado (ex.: DCIM/Antiga/ -> DCIM/Nova/). Usa a mesma
     * estratégia de tentar direto e cair pro fallback de cópia quando necessário (itens da
     * pasta Download, por exemplo, sempre precisam do fallback).
     */
    suspend fun renameDeviceFolder(oldFolderName: String, newFolderName: String): Int =
        withContext(Dispatchers.IO) {
            var failures = 0
            val resolver = getApplication<Application>().contentResolver
            val photosInFolder = _allPhotos.value.filter { it.bucketName == oldFolderName }

            photosInFolder.forEach { photo ->
                val newRelativePath = resolveRenamedRelativePath(photo, newFolderName)
                val movedDirectly = try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, newRelativePath)
                    }
                    resolver.update(photo.uri, values, null, null) > 0
                } catch (e: Exception) {
                    android.util.Log.w("GalleriaRename", "Update direto falhou pra ${photo.uri}: ${e.message}")
                    false
                }

                if (!movedDirectly) {
                    val moved = copyToCollectionAndDeleteOriginal(resolver, photo.uri, photo, newRelativePath)
                    if (!moved) failures++
                }
            }
            loadPhotos()
            failures
        }

    private fun resolveRenamedRelativePath(photo: Photo, newFolderName: String): String {
        val allowedTopDirs = if (photo.isVideo) ALLOWED_VIDEO_TOP_DIRS else ALLOWED_IMAGE_TOP_DIRS
        val base = if (photo.isVideo) "Movies" else "Pictures"
        val root = Environment.getExternalStorageDirectory().absolutePath
        val withoutRoot = photo.path.removePrefix(root).trimStart('/')
        val dir = withoutRoot.substringBeforeLast('/', missingDelimiterValue = "")
        val topDir = dir.substringBefore('/', missingDelimiterValue = dir)

        if (dir.isNotBlank() && topDir in allowedTopDirs && !dir.contains("://")) {
            // Troca só o último pedaço do caminho (o nome da pasta), preservando o resto --
            // ex.: "DCIM/NomeAntigo" -> "DCIM/NomeNovo", "Pictures/Sub/Antiga" -> "Pictures/Sub/Nova".
            val parent = dir.substringBeforeLast('/', missingDelimiterValue = "")
            return if (parent.isBlank() || parent == dir) "$newFolderName/" else "$parent/$newFolderName/"
        }
        return "$base/$newFolderName/"
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
                val uri = Uri.parse(uriString)

                val movedDirectly = try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    }
                    resolver.update(uri, values, null, null) > 0
                } catch (e: Exception) {
                    android.util.Log.w("GalleriaMove", "Update direto falhou pra $uriString -> $relativePath: ${e.message}")
                    false
                }

                if (!movedDirectly) {
                    // Itens em "Download" costumam ficar indexados numa coleção separada do
                    // MediaStore (a de Downloads, não a de Imagens/Vídeos), que só permite o
                    // caminho continuar dentro de "Download" -- o Android rejeita a mudança
                    // direta mesmo com a permissão de escrita concedida (é uma trava do
                    // próprio sistema, não de permissão). O jeito de "mover" nesse caso é
                    // criar uma cópia nova já na coleção certa e apagar a original -- é o que
                    // apps como o Google Gallery fazem por trás dos panos pra esses casos.
                    val moved = copyToCollectionAndDeleteOriginal(resolver, uri, photo, relativePath)
                    if (!moved) failures++
                }
            }
            loadPhotos()
            failures
        }

    private fun copyToCollectionAndDeleteOriginal(
        resolver: ContentResolver,
        sourceUri: Uri,
        photo: Photo?,
        relativePath: String
    ): Boolean {
        return try {
            val displayName = photo?.displayName?.takeIf { it.isNotBlank() } ?: "arquivo_${System.currentTimeMillis()}"
            val mimeType = photo?.mimeType?.takeIf { it.isNotBlank() } ?: "image/*"
            val isVideo = photo?.isVideo == true
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val insertValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val newUri = resolver.insert(collection, insertValues) ?: return false

            val copyOk = resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                    true
                } ?: false
            } ?: false

            if (!copyOk) {
                resolver.delete(newUri, null, null)
                return false
            }

            resolver.update(
                newUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )

            try {
                resolver.delete(sourceUri, null, null)
            } catch (_: Exception) {
                // A cópia já existe e está completa nesse ponto -- se não conseguir apagar o
                // original (raro), fica duplicado, o que é bem mais seguro do que arriscar
                // apagar o original sem garantir que a cópia terminou de verdade.
            }
            true
        } catch (e: Exception) {
            android.util.Log.w("GalleriaMove", "Cópia de fallback falhou pra $sourceUri -> $relativePath: ${e.message}")
            false
        }
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
