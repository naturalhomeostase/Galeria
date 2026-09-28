package com.galeria.ui.screens.videocompress

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.galeria.util.ImageEditUtils
import com.galeria.util.FileUtils
import com.galeria.util.video.VideoCompressor
import kotlinx.coroutines.launch

/**
 * Reduz o tamanho de um vídeo recodificando-o com uma resolução/bitrate menores. Assim como
 * a compressão de imagem no editor, isto nunca mexe no arquivo original -- o resultado é
 * sempre salvo como um item novo na galeria.
 */
private enum class VideoQuality(val label: String, val maxDimension: Int, val bitrate: Int) {
    ALTA("Alta qualidade", 1920, 8_000_000),
    MEDIA("Média", 1280, 4_000_000),
    BAIXA("Menor tamanho", 854, 1_500_000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCompressScreen(
    uriString: String,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalSizeBytes by remember { mutableStateOf<Long?>(null) }
    var quality by remember { mutableStateOf(VideoQuality.MEDIA) }
    var compressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(uriString) {
        originalSizeBytes = ImageEditUtils.getFileSizeBytes(context, Uri.parse(uriString))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comprimir vídeo") },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !compressing) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = uriString,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(8.dp))
            if (originalSizeBytes != null) {
                Text(
                    "Tamanho original: ${FileUtils.formatSize(originalSizeBytes!!)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Qualidade", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoQuality.entries.forEach { option ->
                    FilterChip(
                        selected = quality == option,
                        onClick = { if (!compressing) quality = option },
                        label = { Text(option.label) },
                        enabled = !compressing
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Reduz o maior lado do vídeo para até ${quality.maxDimension}px. Um vídeo já " +
                    "menor que isso mantém a resolução e só reduz o bitrate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            if (compressing) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Comprimindo… isso pode levar alguns minutos, dependendo da duração do vídeo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    compressing = true
                    progress = 0f
                    scope.launch {
                        val result = VideoCompressor.compress(
                            context = context,
                            sourceUri = Uri.parse(uriString),
                            targetMaxDimension = quality.maxDimension,
                            videoBitrate = quality.bitrate,
                            onProgress = { progress = it }
                        )
                        compressing = false
                        if (result.uri != null) {
                            Toast.makeText(context, "Vídeo comprimido salvo na galeria", Toast.LENGTH_SHORT).show()
                            onDone()
                        } else {
                            Toast.makeText(
                                context,
                                result.error ?: "Não foi possível comprimir este vídeo",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !compressing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (compressing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Comprimir")
            }
        }
    }
}
