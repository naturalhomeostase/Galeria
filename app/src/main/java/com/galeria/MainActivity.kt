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

private fun requiredImagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
private fun GaleriaRoot(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val hasPermission by viewModel.hasPermission.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        val permission = requiredImagePermission()
        val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    if (hasPermission) {
        GaleriaNavGraph(viewModel = viewModel)
    } else {
        PermissionRequestScreen { permissionLauncher.launch(requiredImagePermission()) }
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
            "Precisamos de acesso às suas fotos para exibir a galeria.",
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onRequest) { Text("Permitir acesso") }
    }
}
