package com.galeria.util

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.galeria.ui.GalleryViewModel
import kotlinx.coroutines.launch

/**
 * Retorna uma função que move de verdade (no disco, via RELATIVE_PATH do MediaStore) uma
 * lista de fotos/vídeos pra outra pasta do dispositivo. [onCompleted] recebe a quantidade de
 * itens que não puderam ser movidos (0 = tudo certo), pra tela chamadora poder avisar o
 * usuário se algo falhar.
 *
 * No Android 11+ isso pede permissão de escrita pro lote inteiro de uma vez (um popup só,
 * mesmo padrão já usado pra exclusão em lote). Em versões mais antigas tenta direto — pode
 * falhar silenciosamente pra fotos que o app não "criou" (limitação do Android 10 pra baixo).
 */
@Composable
fun rememberMoveToFolderAction(
    viewModel: GalleryViewModel,
    onCompleted: (failures: Int) -> Unit
): (List<String>, String) -> Unit {
    val scope = rememberCoroutineScope()
    var pendingUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingFolder by remember { mutableStateOf("") }

    val writeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uris = pendingUris
        val folder = pendingFolder
        pendingUris = emptyList()
        pendingFolder = ""
        if (result.resultCode == Activity.RESULT_OK && uris.isNotEmpty()) {
            scope.launch {
                val failures = viewModel.applyMoveToFolder(uris, folder)
                onCompleted(failures)
            }
        } else {
            // Usuário cancelou o popup de permissão — não move nada, mas avisa a tela
            // (0 tentativas concluídas, então não é bem uma "falha" pra reportar como erro).
            onCompleted(0)
        }
    }

    return { uriStrings, targetFolderName ->
        if (uriStrings.isEmpty() || targetFolderName.isBlank()) {
            onCompleted(0)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = viewModel.buildMoveWriteRequest(uriStrings)
            if (pendingIntent != null) {
                pendingUris = uriStrings
                pendingFolder = targetFolderName
                writeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else {
                scope.launch {
                    val failures = viewModel.applyMoveToFolder(uriStrings, targetFolderName)
                    onCompleted(failures)
                }
            }
        } else {
            scope.launch {
                val failures = viewModel.applyMoveToFolder(uriStrings, targetFolderName)
                onCompleted(failures)
            }
        }
    }
}

/**
 * Mesma ideia do [rememberMoveToFolderAction], mas para renomear uma pasta do dispositivo
 * inteira. [uris] é só a lista de itens da pasta usada para pedir a permissão de escrita em
 * lote -- a renomeação em si (achar todos os itens da pasta e trocar o caminho de cada um)
 * é feita pelo ViewModel.
 */
@Composable
fun rememberRenameFolderAction(
    viewModel: GalleryViewModel,
    onCompleted: (failures: Int) -> Unit
): (uris: List<String>, oldFolderName: String, newFolderName: String) -> Unit {
    val scope = rememberCoroutineScope()
    var pendingOldName by remember { mutableStateOf("") }
    var pendingNewName by remember { mutableStateOf("") }

    val writeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val oldName = pendingOldName
        val newName = pendingNewName
        pendingOldName = ""
        pendingNewName = ""
        if (result.resultCode == Activity.RESULT_OK && oldName.isNotBlank()) {
            scope.launch {
                val failures = viewModel.renameDeviceFolder(oldName, newName)
                onCompleted(failures)
            }
        } else {
            onCompleted(0)
        }
    }

    return { uris, oldFolderName, newFolderName ->
        if (uris.isEmpty() || newFolderName.isBlank()) {
            onCompleted(0)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = viewModel.buildMoveWriteRequest(uris)
            if (pendingIntent != null) {
                pendingOldName = oldFolderName
                pendingNewName = newFolderName
                writeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else {
                scope.launch {
                    val failures = viewModel.renameDeviceFolder(oldFolderName, newFolderName)
                    onCompleted(failures)
                }
            }
        } else {
            scope.launch {
                val failures = viewModel.renameDeviceFolder(oldFolderName, newFolderName)
                onCompleted(failures)
            }
        }
    }
}
