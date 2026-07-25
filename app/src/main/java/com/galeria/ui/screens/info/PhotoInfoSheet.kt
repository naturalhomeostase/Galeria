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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galeria.data.model.Photo
import com.galeria.util.DateUtils
import com.galeria.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInfoSheet(photo: Photo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Informações da foto", fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow("Nome", photo.displayName)
            InfoRow("Data", DateUtils.fullDate(photo.dateTakenMillis))
            InfoRow("Resolução", "${photo.width} x ${photo.height}")
            InfoRow("Tamanho", FileUtils.formatSize(photo.sizeBytes))
            InfoRow("Formato", photo.mimeType)
            InfoRow("Pasta", photo.bucketName)
            InfoRow("Caminho", photo.path)
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
