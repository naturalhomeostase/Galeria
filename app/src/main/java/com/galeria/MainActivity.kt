package com.galeria

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.navigation.GaleriaNavGraph
import com.galeria.ui.theme.GaleriaTheme

class MainActivity : FragmentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Precisa vir antes de super.onCreate() -- é essa ordem que faz o sistema desenhar a
        // splash (ícone sobre o fundo definido em Theme.Galeria.Splash) enquanto a Activity
        // ainda está inicializando, em vez da tela branca padrão do Android.
        installSplashScreen()
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

    // Depois que o usuário nega e o Android para de mostrar o diálogo (2ª negativa, ou "não
    // perguntar novamente"), chamar launch() de novo não faz nada -- o app ficava travado na
    // tela de permissão sem nenhuma saída. denialCount rastreia se já tentamos pelo menos uma
    // vez nesta sessão, pra podermos diferenciar "ainda não perguntei" de "negado de vez".
    var denialCount by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.onPermissionGranted()
        } else {
            denialCount++
        }
    }

    LaunchedEffect(Unit) {
        if (initiallyGranted) {
            viewModel.onPermissionGranted()
        } else {
            permissionLauncher.launch(requiredMediaPermissions())
        }
    }

    val activity = context as? Activity
    val permanentlyDenied = denialCount > 0 && activity != null &&
        requiredMediaPermissions().none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    if (hasPermission || initiallyGranted) {
        GaleriaNavGraph(viewModel = viewModel)
    } else {
        PermissionRequestScreen(
            permanentlyDenied = permanentlyDenied,
            onRequest = { permissionLauncher.launch(requiredMediaPermissions()) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun PermissionRequestScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            if (permanentlyDenied) {
                "O acesso às fotos e vídeos foi negado. Pra usar a galeria, ative a permissão nas configurações do app."
            } else {
                "Precisamos de acesso às suas fotos e vídeos para exibir a galeria."
            },
            modifier = Modifier.padding(vertical = 16.dp)
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text("Abrir configurações") }
        } else {
            Button(onClick = onRequest) { Text("Permitir acesso") }
        }
    }
}
