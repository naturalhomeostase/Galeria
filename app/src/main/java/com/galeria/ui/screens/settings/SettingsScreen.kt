package com.galeria.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenLargeFiles: () -> Unit
) {
    val context = LocalContext.current
    val showHidden by viewModel.showHiddenAlbums.collectAsState()
    val hiddenFolders by viewModel.hiddenFolderNames.collectAsState()
    val safFolders by viewModel.safFolders.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Pasta oculta"
            viewModel.addSafFolder(uri.toString(), displayName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Lixeira") },
                    supportingContent = { Text("Fotos excluídas recentemente") },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenTrash() }
                )
                ListItem(
                    headlineContent = { Text("Arquivos grandes") },
                    supportingContent = { Text("Encontre fotos que ocupam mais espaço") },
                    leadingContent = { Icon(Icons.Filled.PhotoSizeSelectLarge, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenLargeFiles() }
                )
                Divider()
            }
            item {
                Text(
                    "Tema",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            }
            items(ThemeMode.entries, key = { it.name }) { mode ->
                ListItem(
                    headlineContent = { Text(mode.label) },
                    leadingContent = {
                        RadioButton(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                    },
                    modifier = Modifier.selectable(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                )
            }
            item { Divider() }

            item {
                ListItem(
                    headlineContent = { Text("Mostrar álbuns e pastas que você ocultou") },
                    supportingContent = {
                        Text(
                            "Reexibe, marcados com um ícone, os álbuns e pastas que você mesmo " +
                                "escolheu ocultar dentro do app. Não tem relação com pastas com " +
                                "arquivo .nomedia — pra essas, use \"Adicionar pasta oculta\" mais abaixo."
                        )
                    },
                    trailingContent = {
                        Switch(checked = showHidden, onCheckedChange = { viewModel.setShowHiddenAlbums(it) })
                    }
                )
                Divider()
            }

            if (hiddenFolders.isNotEmpty()) {
                item {
                    Text(
                        "Pastas ocultas nesta lista",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(hiddenFolders.toList(), key = { it }) { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { Icon(Icons.Filled.FolderOff, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.setFolderHidden(name, false) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Reexibir")
                            }
                        }
                    )
                }
                item { Divider() }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pastas ocultas do sistema", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Algumas pastas (como certas pastas de apps de mensagens) têm um arquivo .nomedia " +
                            "e por isso não aparecem no Google Fotos nem são detectadas automaticamente. " +
                            "Adicione manualmente a pasta abaixo para exibi-la aqui mesmo assim.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Button(onClick = { pickFolderLauncher.launch(null) }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                        Text("  Adicionar pasta oculta")
                    }
                }
            }

            items(safFolders, key = { it.id }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.displayName) },
                    supportingContent = { Text("Pasta adicionada manualmente") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeSafFolder(folder.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remover")
                        }
                    }
                )
            }
        }
    }
}
