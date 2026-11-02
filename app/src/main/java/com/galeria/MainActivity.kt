package com.galeria

import android.Manifest
import android.app.Activity
import android.content.ClipData
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
import com.galeria.data.model.Photo
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.navigation.GaleriaNavGraph
import com.galeria.ui.screens.picker.ExternalPickerScreen
import com.galeria.ui.screens.viewer.PhotoViewerScreen
import com.galeria.ui.theme.GaleriaTheme

// Representa um pedido de "me dê uma foto/vídeo" vindo de outro app (upload num site pelo
// navegador, anexo de e-mail, etc.), recebido via ACTION_GET_CONTENT ou ACTION_PICK.
private data class PickerRequest(val mimeType: String, val allowMultiple: Boolean) {
    fun matches(photo: Photo): Boolean = when {
        mimeType == "*/*" -> true
        mimeType.endsWith("/*") -> photo.mimeType.startsWith(mimeType.removeSuffix("*"))
        else -> photo.mimeType.equals(mimeType, ignoreCase = true)
    }
}

private fun resolvePickerRequest(intent: Intent?): PickerRequest? {
    val action = intent?.action ?: return null
    if (action != Intent.ACTION_GET_CONTENT && action != Intent.ACTION_PICK) return null
    val allowMultiple = action == Intent.ACTION_GET_CONTENT &&
        intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    return PickerRequest(mimeType = intent.type ?: "*/*", allowMultiple = allowMultiple)
}

// Pedido de "abrir esta imagem/vídeo" vindo de outro app (ACTION_VIEW), por exemplo do
// gerenciador de arquivos. O app que pediu concede leitura só daquele URI, por isso esse caminho
// não depende da permissão de mídia do Galeria.
private data class ViewRequest(val uri: Uri, val isVideo: Boolean)

private fun resolveViewRequest(activity: Activity): ViewRequest? {
    val intent = activity.intent ?: return null
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    val mime = intent.type ?: try {
        activity.contentResolver.getType(uri)
    } catch (_: Exception) {
        null
    }
    return ViewRequest(uri = uri, isVideo = mime?.startsWith("video/") == true)
}

class MainActivity : FragmentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Precisa vir antes de super.onCreate() -- é essa ordem que faz o sistema desenhar a
        // splash (ícone sobre o fundo definido em Theme.Galeria.Splash) enquanto a Activity
        // ainda está inicializando, em vez da tela branca padrão do Android.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val pickerRequest = resolvePickerRequest(intent)
        val viewRequest = resolveViewRequest(this)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                com.galeria.ui.ThemeMode.SISTEMA -> androidx.compose.foundation.isSystemInDarkTheme()
                com.galeria.ui.ThemeMode.CLARO -> false
                com.galeria.ui.ThemeMode.ESCURO -> true
            }
            GaleriaTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (viewRequest != null) {
                        PhotoViewerScreen(
                            viewModel = viewModel,
                            uris = listOf(viewRequest.uri.toString()),
                            startIndex = 0,
                            onBack = { finish() },
                            onEdit = {},
                            onAddToAlbum = {},
                            onSetWallpaper = {},
                            onCompressVideo = {},
                            externalMode = true,
                            externalIsVideo = viewRequest.isVideo
                        )
                    } else {
                        GaleriaRoot(
                            viewModel = viewModel,
                            pickerRequest = pickerRequest,
                            onPickResult = { uris -> finishWithPickedUris(uris) },
                            onPickCancel = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    // Sair do app (Home, trocar de app, tela bloqueada) tranca o cofre de novo. Mudança de
    // configuração (rotação etc.) não conta: a Activity pode passar por onStop nesses casos e o
    // usuário não saiu de fato.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.lockSecret()
    }

    // Monta o resultado no formato que quem pediu a foto espera de volta: um único URI em
    // "data" pra seleção simples, ou um ClipData com todos os itens pra seleção múltipla
    // (é assim que o seletor de fotos do próprio sistema Android faz). FLAG_GRANT_READ_URI_PERMISSION
    // é o que permite ao app que chamou o Galeria ler essas fotos mesmo sem ter permissão de
    // armazenamento -- sem essa flag, o outro app receberia o URI mas não conseguiria abri-lo.
    private fun finishWithPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val resultIntent = Intent().apply {
            if (uris.size == 1) {
                data = uris.first()
            } else {
                clipData = ClipData.newUri(contentResolver, "Imagens selecionadas", uris.first()).apply {
                    for (i in 1 until uris.size) addItem(ClipData.Item(uris[i]))
                }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

private fun requiredMediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

@Composable
private fun GaleriaRoot(
    viewModel: GalleryViewModel,
    pickerRequest: PickerRequest?,
    onPickResult: (List<Uri>) -> Unit,
    onPickCancel: () -> Unit
) {
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
        if (pickerRequest != null) {
            ExternalPickerScreen(
                viewModel = viewModel,
                allowMultiple = pickerRequest.allowMultiple,
                filter = { photo -> pickerRequest.matches(photo) },
                onPick = onPickResult,
                onCancel = onPickCancel
            )
        } else {
            GaleriaNavGraph(viewModel = viewModel)
        }
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
