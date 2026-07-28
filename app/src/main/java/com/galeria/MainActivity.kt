package com.galeria

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.navigation.GaleriaNavGraph
import com.galeria.ui.theme.GaleriaTheme

class MainActivity : FragmentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                com.galeria.ui.ThemeMode.SISTEMA -> androidx.compose.foundation.isSystemInDarkTheme()
                com.galeria.ui.ThemeMode.CLARO -> false
                com.galeria.ui.ThemeMode.ESCURO -> true
            }
            GaleriaTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GaleriaRoot(viewModel = viewModel)
                }
            }
        }
    }
}

private fun requiredMediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

@Composable
private fun GaleriaRoot(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val hasPermission by viewModel.hasPermission.collectAsState()

    // Verificação síncrona já na primeira composição (não dentro de um LaunchedEffect,
    // que só roda depois do primeiro frame desenhado). É isso que evitava o "flash" da
    // tela de permissão ao reabrir o app quando o acesso já havia sido concedido antes.
    val initiallyGranted = remember {
        requiredMediaPermissions().any {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) viewModel.onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (initiallyGranted) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(requiredMediaPermissions())
        }
    }

    if (hasPermission || initiallyGranted) {
        GaleriaNavGraph(viewModel = viewModel)
    } else {
        PermissionRequestScreen { permissionLauncher.launch(requiredMediaPermissions()) }
    }
}

@Composable
private fun PermissionRequestScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            "Precisamos de acesso às suas fotos e vídeos para exibir a galeria.",
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onRequest) { Text("Permitir acesso") }
    }
}
