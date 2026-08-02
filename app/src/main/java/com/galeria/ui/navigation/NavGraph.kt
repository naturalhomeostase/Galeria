package com.galeria.ui.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.net.URLDecoder
import java.net.URLEncoder

private const val TOP_TABS_ROUTE_PREFIX = ""

@Composable
fun GaleriaNavGraph(viewModel: GalleryViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute == GaleriaTab.FOTOS.route ||
        currentRoute == GaleriaTab.ALBUNS.route ||
        currentRoute == GaleriaTab.FAVORITOS.route

    val bottomBarOpaque by viewModel.bottomBarOpaque.collectAsState()

    // Shared holder for the photo list currently being viewed in the pager
    var viewerUris by remember { mutableStateOf(listOf<String>()) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                GaleriaBottomBar(currentRoute = currentRoute, opaque = bottomBarOpaque) { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(GaleriaTab.ALBUNS.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = androidx.compose.ui.Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = GaleriaTab.ALBUNS.route,
                // Desativa qualquer animação de transição entre telas (o "tremelique" ao abrir
                // um álbum, por exemplo). Num app de galeria, trocar de tela deve ser instantâneo.
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {

                composable(GaleriaTab.FOTOS.route) {
                    HomeScreen(viewModel = viewModel) { uris, index ->
                        viewerUris = uris
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
                        onOpenLargeFiles = { navController.navigate("largeFiles") }
                    )
                }

                composable("largeFiles") {
                    LargeFilesScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenPhoto = { uris, index ->
                            viewerUris = uris
                            navController.navigate("viewer/$index")
                        }
                    )
                }

                composable(GaleriaTab.FAVORITOS.route) {
                    FavoritesScreen(viewModel = viewModel) { uris, index ->
                        viewerUris = uris
                        navController.navigate("viewer/$index")
                    }
                }

                composable(
                    route = "secretGate/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    var unlocked by remember { mutableStateOf(false) }
                    var needsSetup by remember { mutableStateOf<Boolean?>(null) }

                    androidx.compose.runtime.LaunchedEffect(albumId) {
                        needsSetup = !viewModel.hasSecretPassword()
                    }

                    when {
                        needsSetup == null -> Unit
                        needsSetup == true -> SetupSecretScreen(viewModel = viewModel) {
                            navController.navigate("album/$albumId") {
                                popUpTo("secretGate/$albumId") { inclusive = true }
                            }
                        }
                        unlocked -> Unit
                        else -> SecretLockScreen(
                            viewModel = viewModel,
                            onUnlocked = {
                                navController.navigate("album/$albumId") {
                                    popUpTo("secretGate/$albumId") { inclusive = true }
                                }
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }
                }

                composable(
                    route = "album/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    AlbumDetailScreen(
                        viewModel = viewModel,
                        albumId = albumId,
                        onBack = { navController.popBackStack() },
                        onOpenPhoto = { uris, index ->
                            viewerUris = uris
                            navController.navigate("viewer/$index")
                        },
                        onAddPhotos = { navController.navigate("addPhotos/$albumId") }
                    )
                }

                composable(
                    route = "addPhotos/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { entry ->
                    val albumId = entry.arguments?.getLong("albumId") ?: 0L
                    AddPhotosToAlbumScreen(viewModel = viewModel, albumId = albumId) {
                        navController.popBackStack()
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
                            viewerUris = uris
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
                    PhotoViewerScreen(
                        viewModel = viewModel,
                        uris = viewerUris,
                        startIndex = startIndex,
                        onBack = { navController.popBackStack() },
                        onEdit = { uri ->
                            navController.navigate("editor/${encode(uri)}")
                        },
                        onAddToAlbum = { uri ->
                            navController.navigate("pickAlbum/${encode(uri)}")
                        }
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
        }
    }
}

private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")
