package com.galeria.util

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Retorna uma função que move para a lixeira do sistema (ou exclui, em versões antigas do
 * Android, ou em pastas adicionadas manualmente via SAF) uma lista de URIs de fotos.
 * [onCompleted] é chamado depois que a exclusão é confirmada pelo usuário (Android 11+)
 * ou imediatamente após a tentativa, nas demais situações.
 *
 * Quando a exclusão é DEFINITIVA (Android 10 ou anterior, que não tem lixeira, ou itens de
 * pastas adicionadas manualmente, que nunca vão pra lixeira), o sistema não mostra nenhuma
 * confirmação própria -- então este componente mostra uma antes de apagar. No Android 11+ com
 * itens normais, quem confirma é o próprio diálogo do sistema ("mover para a lixeira").
 */
@Composable
fun rememberBulkDeleteAction(onCompleted: () -> Unit): (List<String>) -> Unit {
    val context = LocalContext.current
    var pendingPermanent by remember { mutableStateOf<List<String>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { onCompleted() }

    fun performDelete(uriStrings: List<String>) {
        val parsed = uriStrings.map { Uri.parse(it) }
        val mediaUris = parsed.filter { it.authority == "media" }
        val safUris = parsed.filter { it.authority != "media" }

        var safFailures = 0
        safUris.forEach { uri ->
            val deleted = try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (_: Exception) {
                false
            }
            if (!deleted) safFailures++
        }
        if (safFailures > 0) {
            // Acontece quando a pasta foi adicionada só com permissão de leitura. Antes isso
            // falhava em silêncio e o item simplesmente continuava lá.
            Toast.makeText(
                context,
                if (safFailures == 1) "1 item da pasta adicionada não pôde ser excluído. Adicione a pasta de novo nos Ajustes para permitir a exclusão."
                else "$safFailures itens da pasta adicionada não puderam ser excluídos. Adicione a pasta de novo nos Ajustes para permitir a exclusão.",
                Toast.LENGTH_LONG
            ).show()
        }

        if (mediaUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // createTrashRequest (mover pra lixeira, reversível) -- não confundir com
                // createDeleteRequest (exclusão permanente, sem volta).
                val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, mediaUris, true)
                launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else {
                var mediaFailures = 0
                mediaUris.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: SecurityException) {
                        mediaFailures++
                    }
                }
                if (mediaFailures > 0) {
                    Toast.makeText(
                        context,
                        if (mediaFailures == 1) "1 item não pôde ser excluído" else "$mediaFailures itens não puderam ser excluídos",
                        Toast.LENGTH_LONG
                    ).show()
                }
                onCompleted()
            }
        } else if (safUris.isNotEmpty()) {
            onCompleted()
        }
    }

    pendingPermanent?.let { list ->
        val hasSaf = list.any { Uri.parse(it).authority != "media" }
        val hasTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && list.any { Uri.parse(it).authority == "media" }
        AlertDialog(
            onDismissRequest = { pendingPermanent = null },
            title = { Text("Excluir permanentemente") },
            text = {
                Text(
                    when {
                        hasSaf && hasTrash ->
                            "Os itens das pastas adicionadas manualmente não vão para a lixeira e serão excluídos para sempre. Os demais irão para a lixeira. Deseja continuar?"
                        hasSaf ->
                            if (list.size == 1) "Este item não vai para a lixeira e será excluído para sempre. Deseja continuar?"
                            else "Estes ${list.size} itens não vão para a lixeira e serão excluídos para sempre. Deseja continuar?"
                        else ->
                            if (list.size == 1) "Este Android não tem lixeira: o item será excluído para sempre. Deseja continuar?"
                            else "Este Android não tem lixeira: os ${list.size} itens serão excluídos para sempre. Deseja continuar?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPermanent = null
                    performDelete(list)
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanent = null }) { Text("Cancelar") }
            }
        )
    }

    return { uriStrings ->
        if (uriStrings.isNotEmpty()) {
            val permanent = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                uriStrings.any { Uri.parse(it).authority != "media" }
            if (permanent) pendingPermanent = uriStrings else performDelete(uriStrings)
        }
    }
}
