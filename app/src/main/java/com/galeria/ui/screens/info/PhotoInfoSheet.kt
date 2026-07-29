package com.galeria.ui.screens.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galeria.data.model.Photo
import com.galeria.util.DateUtils
import com.galeria.util.ExifInfo
import com.galeria.util.ExifUtils
import com.galeria.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInfoSheet(photo: Photo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var exifInfo by remember(photo.uri) { mutableStateOf<ExifInfo?>(null) }

    LaunchedEffect(photo.uri, photo.isVideo) {
        // ExifInterface também consegue ler metadados (fabricante/modelo/GPS) embutidos em
        // alguns contêineres de vídeo (mp4/mov), então vale tentar pra vídeo também — se não
        // houver nada, hasCameraInfo fica false e a seção "Câmera" simplesmente não aparece.
        exifInfo = ExifUtils.readExif(context, photo.uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Informações", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow("Nome", photo.displayName)
            InfoRow("Data", DateUtils.fullDate(photo.dateTakenMillis))
            if (photo.isVideo) {
                InfoRow("Duração", DateUtils.formatDuration(photo.durationMs))
            }
            InfoRow("Resolução", "${photo.width} x ${photo.height}")
            InfoRow("Tamanho", FileUtils.formatSize(photo.sizeBytes))
            InfoRow("Formato", photo.mimeType)
            InfoRow("Pasta", photo.bucketName)
            InfoRow("Caminho", photo.path)

            val exif = exifInfo
            if (exif != null && exif.hasCameraInfo) {
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text("Câmera", fontWeight = FontWeight.Bold)
                val deviceLabel = listOfNotNull(exif.cameraMake, exif.cameraModel)
                    .joinToString(" ")
                    .trim()
                if (deviceLabel.isNotBlank()) {
                    InfoRow("Aparelho", deviceLabel)
                }
                exif.focalLength?.let { InfoRow("Distância focal", it) }
                exif.fNumber?.let { InfoRow("Abertura", it) }
                exif.exposureTime?.let { InfoRow("Velocidade do obturador", it) }
                exif.iso?.let { InfoRow("Sensibilidade", it) }
                exif.flash?.let { InfoRow("Flash", it) }
                exif.gpsLatLong?.let { InfoRow("Localização (GPS)", it) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(value)
    }
}
