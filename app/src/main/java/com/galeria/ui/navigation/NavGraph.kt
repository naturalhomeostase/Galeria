package com.galeria.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.galeria.ui.GalleryViewModel
import com.galeria.ui.components.GaleriaBottomBar
import com.galeria.ui.components.GaleriaTab
import com.galeria.ui.screens.about.AboutScreen
import com.galeria.ui.screens.albums.AddPhotosToAlbumScreen
import com.galeria.ui.screens.albums.AlbumDetailScreen
import com.galeria.ui.screens.albums.AlbumsScreen
import com.galeria.ui.screens.albums.DeviceFolderDetailScreen
import com.galeria.ui.screens.editor.EditorScreen
import com.galeria.ui.screens.favorites.FavoritesScreen
import com.galeria.ui.screens.home.HomeScreen
import com.galeria.ui.screens.largefiles.LargeFilesScreen
import com.galeria.ui.screens.secret.SecretLockScreen
import com.galeria.ui.screens.secret.SetupSecretScreen
import com.galeria.ui.screens.settings.SettingsScreen
import com.galeria.ui.screens.trash.TrashScreen
import com.galeria.ui.screens.viewer.PhotoViewerScreen
import com.galeria.ui.screens.videocompress.VideoCompressScreen
import com.galeria.ui.screens.wallpaper.WallpaperPreviewScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val TOP_TABS_ROUTE_PREFIX = ""

// Rotas por onde dá pra chegar em conteúdo do cofre (o álbum secreto e o que se abre a partir
// dele). Enquanto o cofre está destrancado, tudo o que está empilhado acima da lista de álbuns
// pertence a essa área; ao sair dela, o cofre tranca de novo.
private val SECRET_AREA_ROUTES = setOf(
    "secretGate/{albumId}",
    "album/{albumId}",
    "addPhotos/{albumId}",
    "viewer/{startIndex}",
    "editor/{uri}",
    "pickAlbum/{uri}",
    "wallpaper/{uri}",
    "compressVideo/{uri}"
)

private fun NavController.backToAlbums() {
    popBackStack(GaleriaTab.ALBUNS.route, inclusive = false)
}

/**
 * Só mostra [content] se o álbum não for secreto, ou se for e o cofre estiver destrancado.
 * Se estiver trancado (app voltou do segundo plano, estado restaurado depois de o processo ser
 * morto...), volta para a lista de álbuns em vez de exibir o conteúdo.
 */
@Composable
private fun SecretAlbumGuard(
    viewModel: GalleryViewModel,
    navController: NavController,
    albumId: Long,
    content: @Composable () -> Unit
) {
    val unlocked by viewModel.secretUnlocked.collectAsState()
    val isSecret by produceState<Boolean?>(initialValue = null, albumId) {
        value = viewModel.isAlbumSecret(albumId)
    }
    when {
        isSecret == null -> Unit
        isSecret == true && !unlocked -> LaunchedEffect(Unit) { navController.backToAlbums() }
        else -> content()
    }
}

@Composable
fun GaleriaNavGraph(viewModel: GalleryViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == GaleriaTab.FOTOS.route ||
        currentRoute == GaleriaTab.ALBUNS.route ||
        currentRoute == GaleriaTab.FAVORITOS.route

    val bottomBarOpaque by viewModel.bottomBarOpaque.collectAsState()
    val selectionModeActive by viewModel.selectionModeActive.collectAsState()
    val secretUnlocked by viewModel.secretUnlocked.collectAsState()

    // Tranca o cofre assim que o usuário navega para fora da área dele (lista de álbuns,
    // outras abas, ajustes...).
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route !in SECRET_AREA_ROUTES) viewModel.lockSecret()
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // Quando o cofre tranca por outro motivo (o app foi pro segundo plano -- MainActivity.onStop),
    // quem estava dentro da área do cofre é levado de volta à lista de álbuns.
    var wasUnlocked by remember { mutableStateOf(false) }
    LaunchedEffect(secretUnlocked) {
        if (secretUnlocked) {
            wasUnlocked = true
        } else if (wasUnlocked) {
            wasUnlocked = false
            if (navController.currentDestination?.route in SECRET_AREA_ROUTES) {
                navController.backToAlbums()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = GaleriaTab.ALBUNS.route,
            modifier = Modifier.fillMaxSize(),
            // Desativa qualquer animação de transição entre telas (o "tremelique" ao abrir
            // um álbum, por exemplo). Num app de galeria, trocar de tela deve ser instantâneo.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {

            composable(GaleriaTab.FOTOS.route) {
                HomeScreen(viewModel = viewModel) { uris, index ->
                    viewModel.viewerUris = uris
                    navController.navigate("viewer/$index")
                }
            }

            composable(GaleriaTab.ALBUNS.route) {
                AlbumsScreen(
                    viewModel = viewModel,
                    onOpenAlbum = { albumId, isSecret ->
                        if (isSecret) {
                            navController.navigate("secretGate/$albumId")
                        } else {
                            navController.navigate("album/$albumId")
                        }
                    },
                        onOpenDeviceFolder = { name ->
                            navController.navigate("folder/${encode(name)}")
                        },
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenTrash = { navController.navigate("trash") },
                        onOpenLargeFiles = { navController.navigate("largeFiles") },
                        onOpenAbout = { navController.navigate("about") }
                    )
                }

                composable("about") {
                    AboutScreen(onBack = { navController.popBackStack() })
                }

                composable("largeFiles") {
                    LargeFilesScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenPhoto = { uris, index ->
                            viewModel.viewerUris = uris
                            navController.navigate("viewer/$index")
                        }
                    )
                }

                composable(GaleriaTab.FAVORITOS.route) {
                    FavoritesScreen(viewModel = viewModel) { uris, index ->
                        viewModel.viewerUris = uris
                        navController.navigate("viewer/$index")
                    }
                }

                composable(
                    route = "secretGate/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    var needsSetup by remember { mutableStateOf<Boolean?>(null) }

                    LaunchedEffect(albumId) {
                        needsSetup = !viewModel.hasSecretPassword()
                    }

                    // Destranca ANTES de navegar: a tela do álbum só aparece se o cofre estiver
                    // destrancado (SecretAlbumGuard).
                    val openAlbum = {
                        viewModel.unlockSecret()
                        navController.navigate("album/$albumId") {
                            popUpTo("secretGate/$albumId") { inclusive = true }
                        }
                    }

                    when {
                        needsSetup == null -> Unit
                        needsSetup == true -> SetupSecretScreen(viewModel = viewModel) { openAlbum() }
                        else -> SecretLockScreen(
                            viewModel = viewModel,
                            onUnlocked = { openAlbum() },
                            onCancel = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = "album/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    SecretAlbumGuard(viewModel, navController, albumId) {
                        AlbumDetailScreen(
                            viewModel = viewModel,
                            albumId = albumId,
                            onBack = { navController.popBackStack() },
                            onOpenPhoto = { uris, index ->
                                viewModel.viewerUris = uris
                                navController.navigate("viewer/$index")
                            },
                            onAddPhotos = { navController.navigate("addPhotos/$albumId") }
                        )
                    }
                }

                composable(
                    route = "addPhotos/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    SecretAlbumGuard(viewModel, navController, albumId) {
                        AddPhotosToAlbumScreen(viewModel = viewModel, albumId = albumId) {
                            navController.popBackStack()
                        }
                    }
                }

                composable(
                    route = "folder/{name}",
                    arguments = listOf(navArgument("name") { type = NavType.StringType })
                ) { entry ->
                    val name = decode(entry.arguments?.getString("name") ?: "")
                    DeviceFolderDetailScreen(
                        viewModel = viewModel,
                        folderName = name,
                        onBack = { navController.popBackStack() },
                        onOpenPhoto = { uris, index ->
                            viewModel.viewerUris = uris
                            navController.navigate("viewer/$index")
                        }
                    )
                }

                composable("trash") {
                    TrashScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }

                composable(
                    route = "viewer/{startIndex}",
                    arguments = listOf(navArgument("startIndex") { type = NavType.IntType })
                ) { entry ->
                    val startIndex = entry.arguments?.getInt("startIndex") ?: 0
                    val viewerList = viewModel.viewerUris
                    if (viewerList.isEmpty()) {
                        // Só acontece se o Android matou o processo e restaurou esta tela sem a
                        // lista de fotos: em vez de mostrar um visualizador em branco, volta.
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }
                    // Se a lista contém fotos de álbum secreto e o cofre está trancado (voltou
                    // do segundo plano, ou a foto acabou de ser colocada num álbum secreto), não
                    // mostra: volta pra lista de álbuns.
                    val secretUris by viewModel.secretPhotoUris.collectAsState()
                    val vaultUnlocked by viewModel.secretUnlocked.collectAsState()
                    val hasSecretPhotos = secretUris?.let { secret -> viewerList.any { it in secret } }
                    if (hasSecretPhotos == null) return@composable
                    if (hasSecretPhotos && !vaultUnlocked) {
                        // backToAlbums() é idempotente (nunca desempilha a própria lista de
                        // álbuns), então é seguro disparar de mais de um lugar.
                        LaunchedEffect(Unit) { navController.backToAlbums() }
                        return@composable
                    }
                    PhotoViewerScreen(
                        viewModel = viewModel,
                        uris = viewerList,
                        startIndex = startIndex,
                        onBack = { navController.popBackStack() },
                        onEdit = { uri ->
                            navController.navigate("editor/${encode(uri)}")
                        },
                        onAddToAlbum = { uri ->
                            navController.navigate("pickAlbum/${encode(uri)}")
                        },
                        onSetWallpaper = { uri ->
                            navController.navigate("wallpaper/${encode(uri)}")
                        },
                        onCompressVideo = { uri ->
                            navController.navigate("compressVideo/${encode(uri)}")
                        }
                    )
                }

                composable(
                    route = "compressVideo/{uri}",
                    arguments = listOf(navArgument("uri") { type = NavType.StringType })
                ) { entry ->
                    val uriStr = decode(entry.arguments?.getString("uri") ?: "")
                    VideoCompressScreen(
                        uriString = uriStr,
                        onDone = {
                            viewModel.loadPhotos()
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "wallpaper/{uri}",
                    arguments = listOf(navArgument("uri") { type = NavType.StringType })
                ) { entry ->
                    val uriStr = decode(entry.arguments?.getString("uri") ?: "")
                    WallpaperPreviewScreen(
                        uriString = uriStr,
                        onDone = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "editor/{uri}",
                    arguments = listOf(navArgument("uri") { type = NavType.StringType })
                ) { entry ->
                    val uriStr = decode(entry.arguments?.getString("uri") ?: "")
                    EditorScreen(
                        photoUri = Uri.parse(uriStr),
                        onClose = { navController.popBackStack() },
                        onSaved = {
                            viewModel.loadPhotos()
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = "pickAlbum/{uri}",
                    arguments = listOf(navArgument("uri") { type = NavType.StringType })
                ) { entry ->
                    val uriStr = decode(entry.arguments?.getString("uri") ?: "")
                    PickAlbumForPhotoScreen(
                        viewModel = viewModel,
                        photoUri = uriStr,
                        onDone = { navController.popBackStack() }
                    )
                }
            }

            if (showBottomBar && !selectionModeActive) {
                GaleriaBottomBar(
                    currentRoute = currentRoute,
                    opaque = bottomBarOpaque,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(GaleriaTab.ALBUNS.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")
