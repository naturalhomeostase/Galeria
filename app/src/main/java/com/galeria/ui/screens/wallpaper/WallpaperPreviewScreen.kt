package com.galeria.ui.screens.wallpaper

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.galeria.util.ImageEditUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WallpaperTarget(val label: String) {
    LOCK("Tela de bloqueio"),
    HOME("Tela inicial"),
    BOTH("Ambas")
}

/**
 * Posiciona e aplica uma imagem como papel de parede sem sair do app (nada de abrir outro
 * app pra recortar) -- arrasta pra reposicionar e belisca pra dar zoom, igual o seletor de
 * papel de parede nativo do Android.
 */
@Composable
fun WallpaperPreviewScreen(uriString: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(WallpaperTarget.BOTH) }

    LaunchedEffect(uriString) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                ImageEditUtils.loadBitmap(context, Uri.parse(uriString))
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap == null) loadFailed = true
    }

    // Zoom (>= 1f, 1f = a imagem cobrindo a moldura do jeitinho que caberia sem sobrar nem
    // faltar) e deslocamento em pixels a partir do centro. Os dois são sempre limitados pra
    // moldura nunca ficar com um pedaço vazio (a imagem sempre cobre 100% dela).
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val bmp = bitmap
        if (bmp != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { frameSize = it }
                    .pointerInput(bmp) {
                        detectTransformGestures { _, pan, zoomDelta, _ ->
                            val frameW = frameSize.width.toFloat()
                            val frameH = frameSize.height.toFloat()
                            if (frameW <= 0f || frameH <= 0f) return@detectTransformGestures

                            val baseScale = maxOf(frameW / bmp.width, frameH / bmp.height)
                            val newZoom = (zoom * zoomDelta).coerceIn(1f, 6f)
                            val renderedW = bmp.width * baseScale * newZoom
                            val renderedH = bmp.height * baseScale * newZoom
                            val maxOffsetX = ((renderedW - frameW) / 2f).coerceAtLeast(0f)
                            val maxOffsetY = ((renderedH - frameH) / 2f).coerceAtLeast(0f)

                            zoom = newZoom
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }
                    }
            ) {
                val frameW = frameSize.width.toFloat()
                val frameH = frameSize.height.toFloat()
                if (frameW > 0f && frameH > 0f) {
                    val baseScale = maxOf(frameW / bmp.width, frameH / bmp.height)
                    val effectiveScale = baseScale * zoom
                    val renderedW = bmp.width * effectiveScale
                    val renderedH = bmp.height * effectiveScale
                    val topLeftX = (frameW - renderedW) / 2f + offset.x
                    val topLeftY = (frameH - renderedH) / 2f + offset.y

                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(
                                with(density) { bmp.width.toDp() },
                                with(density) { bmp.height.toDp() }
                            )
                            .graphicsLayer {
                                scaleX = effectiveScale
                                scaleY = effectiveScale
                                transformOrigin = TransformOrigin(0f, 0f)
                                translationX = topLeftX
                                translationY = topLeftY
                            }
                    )
                }
            }
        } else if (loadFailed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Não foi possível carregar essa imagem.", color = Color.White)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        FilledTonalIconButton(
            onClick = onDone,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Cancelar")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperTarget.entries.forEach { option ->
                    FilterChip(
                        selected = target == option,
                        onClick = { target = option },
                        label = { Text(option.label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val bmp = bitmap ?: return@Button
                    val frameW = frameSize.width.toFloat()
                    val frameH = frameSize.height.toFloat()
                    if (frameW <= 0f || frameH <= 0f) return@Button
                    applying = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                val baseScale = maxOf(frameW / bmp.width, frameH / bmp.height)
                                val effectiveScale = baseScale * zoom
                                val renderedW = bmp.width * effectiveScale
                                val renderedH = bmp.height * effectiveScale
                                val topLeftX = (frameW - renderedW) / 2f + offset.x
                                val topLeftY = (frameH - renderedH) / 2f + offset.y

                                val cropLeft = ((0f - topLeftX) / effectiveScale).toInt().coerceIn(0, bmp.width - 1)
                                val cropTop = ((0f - topLeftY) / effectiveScale).toInt().coerceIn(0, bmp.height - 1)
                                val cropRight = ((frameW - topLeftX) / effectiveScale).toInt().coerceIn(cropLeft + 1, bmp.width)
                                val cropBottom = ((frameH - topLeftY) / effectiveScale).toInt().coerceIn(cropTop + 1, bmp.height)
                                val cropRect = Rect(cropLeft, cropTop, cropRight, cropBottom)

                                val wm = WallpaperManager.getInstance(context)
                                val flags = when (target) {
                                    WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                                    WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                                }
                                wm.setBitmap(bmp, cropRect, true, flags)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                        applying = false
                        Toast.makeText(
                            context,
                            if (ok) "Papel de parede aplicado" else "Não foi possível aplicar o papel de parede",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) onDone()
                    }
                },
                enabled = bitmap != null && !applying
            ) {
                if (applying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Definir papel de parede")
            }
        }
    }
}
