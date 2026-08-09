package com.galeria.ui.screens.trash

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import com.galeria.data.model.Photo
import com.galeria.ui.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit
) {
    val trashed by viewModel.trashedPhotos.collectAsState()
    val gridColumns by viewModel.photoGridColumns.collectAsState()
    var photoToDelete by remember { mutableStateOf<Photo?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { viewModel.loadTrash() }

    // Restaurar da lixeira também precisa desse mesmo fluxo de permissão -- a maioria das
    // fotos não foi "criada" pelo nosso app, então uma tentativa direta (sem esse pedido de
    // permissão) sempre falhava silenciosamente, e o botão de restaurar parecia não fazer nada.
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.loadTrash()
            viewModel.loadPhotos()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadTrash() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lixeira") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "A lixeira do sistema requer Android 11 ou superior.",
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (trashed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("A lixeira está vazia.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    items(trashed, key = { it.id }) { photo ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.small)
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TrashActionIcon(Icons.Filled.Restore, "Restaurar") {
                                    val pendingIntent = android.provider.MediaStore.createTrashRequest(
                                        context.contentResolver,
                                        listOf(photo.uri),
                                        false
                                    )
                                    restoreLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                }
                                TrashActionIcon(Icons.Filled.DeleteForever, "Excluir para sempre") {
                                    photoToDelete = photo
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Excluir permanentemente") },
            text = { Text("Esta ação não pode ser desfeita. Deseja excluir esta foto para sempre?") },
            confirmButton = {
                TextButton(onClick = {
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(photo.uri)
                    )
                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    photoToDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun TrashActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label)
    }
}
