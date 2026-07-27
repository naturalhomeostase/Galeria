package com.galeria.util

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Retorna uma função que move para a lixeira do sistema (ou exclui, em versões antigas do
 * Android, ou em pastas adicionadas manualmente via SAF) uma lista de URIs de fotos.
 * [onCompleted] é chamado depois que a exclusão é confirmada pelo usuário (Android 11+)
 * ou imediatamente após a tentativa, nas demais situações.
 */
@Composable
fun rememberBulkDeleteAction(onCompleted: () -> Unit): (List<String>) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { onCompleted() }

    return { uriStrings ->
        val parsed = uriStrings.map { Uri.parse(it) }
        val mediaUris = parsed.filter { it.authority == "media" }
        val safUris = parsed.filter { it.authority != "media" }

        safUris.forEach { uri ->
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (_: Exception) {
            }
        }

        if (mediaUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, mediaUris)
                launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else {
                mediaUris.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: SecurityException) {
                    }
                }
                onCompleted()
            }
        } else if (safUris.isNotEmpty()) {
            onCompleted()
        }
    }
}
