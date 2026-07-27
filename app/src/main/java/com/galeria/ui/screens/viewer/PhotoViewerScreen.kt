package com.galeria.ui.screens.viewer

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.screens.info.PhotoInfoSheet
import kotlin.math.max
import kotlin.math.min

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
    val favorites by viewModel.favoriteUris.collectAsState()
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, max(uris.size - 1, 0))) { uris.size }
    var showInfo by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }

    val currentUri = uris.getOrNull(pagerState.currentPage)
    val currentPhoto = currentUri?.let { viewModel.getPhotoByUri(it) }
    val isCurrentVideo = currentPhoto?.isVideo == true

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val uriStr = uris.getOrNull(page) ?: return@HorizontalPager
            val photo = viewModel.getPhotoByUri(uriStr)
            if (photo?.isVideo == true) {
                VideoPage(uriStr = uriStr, isCurrentPage = page == pagerState.currentPage)
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
                            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                        } else if (uri.authority == "media") {
                            try {
                                context.contentResolver.delete(uri, null, null)
                            } catch (_: SecurityException) {
                            }
                            onBack()
                        } else {
                            try {
                                android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
                            } catch (_: Exception) {
                            }
                            onBack()
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
private fun VideoPage(uriStr: String, isCurrentPage: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(uriStr) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    DisposableEffect(uriStr) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) exoPlayer.play() else exoPlayer.pause()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        }
    )
}

@Composable
private fun ZoomableImage(uriStr: String, onTap: () -> Unit) {
    var targetScale by remember(uriStr) { mutableStateOf(1f) }
    var offsetX by remember(uriStr) { mutableStateOf(0f) }
    var offsetY by remember(uriStr) { mutableStateOf(0f) }

    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(200), label = "zoomScale")
    val isZoomed = targetScale > 1f

    fun toggleZoom() {
        if (targetScale > 1f) {
            targetScale = 1f
            offsetX = 0f
            offsetY = 0f
        } else {
            targetScale = MAX_ZOOM
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = Uri.parse(uriStr),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(uriStr) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { toggleZoom() }
                    )
                }
                .then(
                    if (isZoomed) {
                        Modifier.pointerInput(uriStr, scale) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                offsetX = (offsetX + dragAmount.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        )
    }
}
