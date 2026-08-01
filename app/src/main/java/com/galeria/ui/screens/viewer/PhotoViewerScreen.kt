package com.galeria.ui.screens.viewer

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.LayoutInflater
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import com.galeria.R
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.screens.info.PhotoInfoSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private const val MAX_ZOOM = 4f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    viewModel: GalleryViewModel,
    uris: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddToAlbum: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val favorites by viewModel.favoriteUris.collectAsState()
    // Cópia local e mutável da lista recebida: ao excluir uma foto removemos ela daqui na
    // hora (o app.mediaStoreRepository/_allPhotos do ViewModel só é atualizado de forma
    // assíncrona via loadPhotos(), então depender dele pra decidir o que mostrar em seguida
    // deixava o visualizador momentaneamente mostrando a foto já excluída).
    val localUris = remember(uris) { mutableStateListOf(*uris.toTypedArray()) }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, max(uris.size - 1, 0))) { localUris.size }
    var showInfo by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var pendingDeleteUri by remember { mutableStateOf<String?>(null) }

    // Modo imersivo: some com a barra de status/navegação enquanto o visualizador está aberto
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Remove a foto/vídeo excluído da lista local e pula pra próxima página (ou volta pro
    // álbum se não sobrar nada). Também dispara loadPhotos() em segundo plano pra sincronizar
    // o restante do app (grades de álbuns etc.) com a exclusão.
    fun removeUriAndAdvance(uriString: String) {
        val idx = localUris.indexOf(uriString)
        if (idx >= 0) localUris.removeAt(idx)
        viewModel.loadPhotos()
        if (localUris.isEmpty()) {
            onBack()
        } else {
            val target = idx.coerceIn(0, localUris.size - 1)
            scope.launch { pagerState.scrollToPage(target) }
        }
    }

    val currentUri = localUris.getOrNull(pagerState.currentPage)
    val currentPhoto = currentUri?.let { viewModel.getPhotoByUri(it) }
    val isCurrentVideo = currentPhoto?.isVideo == true

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val deletedUri = pendingDeleteUri
        pendingDeleteUri = null
        if (result.resultCode == Activity.RESULT_OK && deletedUri != null) {
            removeUriAndAdvance(deletedUri)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val uriStr = localUris.getOrNull(page) ?: return@HorizontalPager
            val photo = viewModel.getPhotoByUri(uriStr)
            if (photo?.isVideo == true) {
                VideoPage(
                    uriStr = uriStr,
                    isCurrentPage = page == pagerState.currentPage,
                    chromeVisible = chromeVisible,
                    onTap = { chromeVisible = !chromeVisible }
                )
            } else {
                ZoomableImage(
                    uriStr = uriStr,
                    onTap = { chromeVisible = !chromeVisible }
                )
            }
        }

        if (chromeVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val isFav = currentUri != null && favorites.contains(currentUri)

                IconButton(onClick = { currentUri?.let { viewModel.toggleFavorite(it) } }) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Favoritar",
                        tint = if (isFav) Color(0xFFFFC107) else Color.White
                    )
                }
                if (!isCurrentVideo) {
                    IconButton(onClick = { currentUri?.let(onEdit) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = Color.White)
                    }
                }
                IconButton(onClick = { currentUri?.let(onAddToAlbum) }) {
                    Icon(Icons.Filled.PhotoAlbum, contentDescription = "Adicionar a álbum", tint = Color.White)
                }
                if (!isCurrentVideo) {
                    IconButton(onClick = {
                        currentUri?.let { uriString ->
                            val wallpaperIntent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                                setDataAndType(Uri.parse(uriString), "image/*")
                                putExtra("mimeType", "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(Intent.createChooser(wallpaperIntent, "Usar como"))
                            } catch (_: Exception) {
                                val wm = WallpaperManager.getInstance(context)
                                context.startActivity(wm.getCropAndSetWallpaperIntent(Uri.parse(uriString)))
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Wallpaper, contentDescription = "Usar como", tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    currentUri?.let { uriString ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (isCurrentVideo) "video/*" else "image/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Compartilhar", tint = Color.White)
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Informações", tint = Color.White)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Color.White)
                }
            }
        }
    }

    if (showInfo) {
        val photo = currentUri?.let { viewModel.getPhotoByUri(it) }
        if (photo != null) {
            PhotoInfoSheet(photo = photo, onDismiss = { showInfo = false })
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (isCurrentVideo) "Excluir vídeo" else "Excluir foto") },
            text = { Text("Este item será movido para a lixeira do sistema. Deseja continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    currentUri?.let { uriString ->
                        val uri = Uri.parse(uriString)
                        if (uri.authority == "media" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            pendingDeleteUri = uriString
                            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        } else if (uri.authority == "media") {
                            try {
                                context.contentResolver.delete(uri, null, null)
                                removeUriAndAdvance(uriString)
                            } catch (_: SecurityException) {
                            }
                        } else {
                            try {
                                android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                                removeUriAndAdvance(uriString)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun VideoPage(
    uriStr: String,
    isCurrentPage: Boolean,
    chromeVisible: Boolean,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(uriStr) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            repeatMode = Player.REPEAT_MODE_ONE
            // Preparar (e portanto alocar o decodificador de hardware) só acontece no
            // LaunchedEffect(isCurrentPage) abaixo, quando a página vira a atual — não aqui.
            // Preparar todo vídeo assim que ele é composto (inclusive páginas vizinhas que o
            // Pager mantém compostas durante o gesto de arraste) deixava várias instâncias de
            // decodificador ativas ao mesmo tempo. A maioria dos aparelhos só permite um
            // número pequeno de decodificadores de vídeo simultâneos (às vezes só 1 ou 2 pra
            // certos codecs) — ao estourar esse limite, o vídeo "excedente" falha ao preparar
            // e fica com tela preta permanente, sempre nos mesmos vídeos (os que acabam caindo
            // além do limite do aparelho).
        }
    }

    var isPlaying by remember(uriStr) { mutableStateOf(true) }
    var positionMs by remember(uriStr) { mutableStateOf(0L) }
    var durationMs by remember(uriStr) { mutableStateOf(0L) }
    var isSeeking by remember(uriStr) { mutableStateOf(false) }
    var hasError by remember(uriStr) { mutableStateOf(false) }

    DisposableEffect(uriStr) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) hasError = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            // player.release() é uma chamada bloqueante (derruba decodificador, superfície de
            // vídeo, threads internas) que pode levar dezenas/centenas de ms — se rodar direto
            // aqui, ela trava bem o frame em que a navegação de volta acontece, dando aquela
            // sensação de "demorinha" antes de voltar para o álbum. Adiar pra próxima iteração
            // do loop principal deixa a transição de navegação acontecer primeiro, sem travar.
            val playerToRelease = exoPlayer
            Handler(Looper.getMainLooper()).post {
                playerToRelease.release()
            }
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        } else {
            exoPlayer.pause()
            // Libera o decodificador assim que a página deixa de ser a atual, em vez de
            // segurá-lo até o dispose completo — é o que garante que só a página visível
            // tenha um decodificador ativo por vez.
            if (exoPlayer.playbackState != Player.STATE_IDLE) {
                exoPlayer.stop()
            }
        }
    }

    LaunchedEffect(uriStr) {
        while (true) {
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(300)
        }
    }

    // Estado de zoom próprio dessa página, reiniciado sempre que o vídeo muda (mesma ideia do
    // ZoomableImage usado para fotos) para não "herdar" o zoom do vídeo anterior ao trocar de página.
    val zoomableState = rememberZoomableState()

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .zoomable(state = zoomableState, onClick = { onTap() }),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.galeria_video_player_view, null) as PlayerView).apply {
                    player = exoPlayer
                }
            }
        )

        if (hasError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Não foi possível carregar este vídeo", color = Color.White)
                    TextButton(onClick = {
                        hasError = false
                        exoPlayer.prepare()
                    }) {
                        Text("Tentar novamente", color = Color.White)
                    }
                }
            }
        } else if (chromeVisible) {
            IconButton(
                onClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            if (durationMs > 0) {
                MinimalVideoSlider(
                    value = positionMs.toFloat(),
                    valueRange = 0f..durationMs.toFloat(),
                    onValueChange = {
                        isSeeking = true
                        positionMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(positionMs)
                        isSeeking = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 72.dp)
                )
            }
        }
    }
}

/**
 * Barra de progresso de vídeo fina e minimalista, no lugar do Slider padrão do Material3
 * (que tinha uma trilha e um polegar grossos e chamativos demais para um player de mídia).
 */
@Composable
private fun MinimalVideoSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier.height(24.dp)
    ) {
        val trackWidthDp = maxWidth
        val thumbDiameter = 10.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .pointerInput(valueRange) {
                    detectTapGestures(onTap = { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + frac * range)
                        onValueChangeFinished()
                    })
                }
                .pointerInput(valueRange) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + frac * range)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + frac * range)
                        },
                        onDragEnd = { onValueChangeFinished() },
                        onDragCancel = { onValueChangeFinished() }
                    )
                }
        ) {
            // Trilha inativa
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
            // Trilha ativa (progresso)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
            // Polegar pequeno e discreto
            val thumbOffset = (trackWidthDp * fraction) - (thumbDiameter / 2)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Visualizador de imagem com zoom por pinça e arraste fluido, usando a biblioteca open source
 * Telephoto (https://github.com/saket/telephoto), feita especificamente para esse cenário —
 * zoom de imagem dentro de um carrossel/pager de fotos, coexistindo bem com o swipe entre
 * fotos quando não está ampliada. Depois de algumas tentativas escrevendo esse gesto na mão
 * (com bugs de fluidez que não ficaram bons o suficiente), troquei para essa biblioteca já
 * testada e otimizada por muita gente para exatamente esse caso de uso.
 */
@Composable
private fun ZoomableImage(uriStr: String, onTap: () -> Unit) {
    val context = LocalContext.current
    ZoomableAsyncImage(
        model = ImageRequest.Builder(context)
            .data(Uri.parse(uriStr))
            .crossfade(200)
            .build(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        onClick = { onTap() }
    )
}
