package `is`.xyz.mpv.anime

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.compose.material.icons.filled.Book
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `is`.xyz.mpv.anime.manga.MangaDetailsScreen
import `is`.xyz.mpv.anime.manga.MangaDetailsViewModel
import `is`.xyz.mpv.anime.manga.MangaHomeViewModel
import `is`.xyz.mpv.anime.manga.MangaReaderScreen
import `is`.xyz.mpv.anime.manga.MangaReaderViewModel
import `is`.xyz.mpv.anime.manga.MangaScreen
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_MANGA = "manga"
private const val ROUTE_MANGA_DETAILS = "manga_details"
private const val ROUTE_MANGA_READER = "manga_reader"
private const val ROUTE_DETAILS = "details"

@Composable
fun AnimeApp(
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isDetails = route?.startsWith(ROUTE_DETAILS) == true ||
            route?.startsWith(ROUTE_MANGA_DETAILS) == true ||
            route?.startsWith(ROUTE_MANGA_READER) == true

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resumingEpisode by remember { mutableStateOf<String?>(null) }

    val onContinueWatching: (HistoryItem) -> Unit = { item ->
        if (resumingEpisode == null) {
            resumingEpisode = item.episodeId
            scope.launch {
                try {
                    resumeFromHistory(context, item)
                } catch (t: Throwable) {
                    Toast.makeText(context, t.userMessage(), Toast.LENGTH_LONG).show()
                } finally {
                    resumingEpisode = null
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (!isDetails) AnimeTopBar(onSettings = onOpenSettings)
        },
        bottomBar = {
            if (!isDetails) {
                AnimeBottomBar(
                    currentRoute = route,
                    onHome = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onSearch = {
                        navController.navigate(ROUTE_SEARCH) {
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLibrary = {
                        navController.navigate(ROUTE_LIBRARY) {
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onManga = {
                        navController.navigate(ROUTE_MANGA) {
                            popUpTo(ROUTE_HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onFiles = onOpenFiles,
                    onSettings = onOpenSettings,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_HOME) {
                val viewModel: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = viewModel,
                    onAnimeClick = { card ->
                        navController.navigate("$ROUTE_DETAILS/${Uri.encode(card.slug)}")
                    },
                    onContinueWatching = onContinueWatching,
                )
            }

            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    onOpenDetails = { slug ->
                        navController.navigate("$ROUTE_DETAILS/${Uri.encode(slug)}")
                    },
                    onOpenMangaDetails = { mangaId ->
                        navController.navigate("$ROUTE_MANGA_DETAILS/$mangaId")
                    },
                    onOpenMangaReader = { mangaId, chapterId, chapterNumber ->
                        navController.navigate("$ROUTE_MANGA_READER/$mangaId/$chapterId/${Uri.encode(chapterNumber)}")
                    },
                )
            }

            composable(ROUTE_SEARCH) {
                val viewModel: SearchViewModel = viewModel()
                SearchScreen(viewModel = viewModel, onAnimeClick = { card ->
                    navController.navigate("$ROUTE_DETAILS/${Uri.encode(card.slug)}")
                })
            }

            composable(ROUTE_MANGA) {
                val viewModel: MangaHomeViewModel = viewModel()
                MangaScreen(
                    viewModel = viewModel,
                    onMangaClick = { card ->
                        navController.navigate("$ROUTE_MANGA_DETAILS/${card.id}")
                    },
                    onContinueReading = { item ->
                        navController.navigate(
                            "$ROUTE_MANGA_READER/${item.mangaId}/${item.chapterId}/${Uri.encode(item.chapterNumber)}"
                        )
                    },
                )
            }

            composable(
                route = "$ROUTE_MANGA_DETAILS/{mangaId}",
                arguments = listOf(navArgument("mangaId") { type = NavType.StringType }),
            ) { entry ->
                val mangaId = entry.arguments?.getString("mangaId").orEmpty()
                val viewModel: MangaDetailsViewModel = viewModel()
                MangaDetailsScreen(
                    viewModel = viewModel,
                    mangaId = mangaId,
                    onBack = { navController.popBackStack() },
                    onChapterClick = { chapter ->
                        navController.navigate(
                            "$ROUTE_MANGA_READER/$mangaId/${chapter.id}/${Uri.encode(chapter.chapterNumber)}"
                        )
                    },
                )
            }

            composable(
                route = "$ROUTE_MANGA_READER/{mangaId}/{chapterId}/{chapterNumber}",
                arguments = listOf(
                    navArgument("mangaId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                    navArgument("chapterNumber") { type = NavType.StringType },
                ),
            ) { entry ->
                val mangaId = entry.arguments?.getString("mangaId").orEmpty()
                val chapterId = entry.arguments?.getString("chapterId").orEmpty()
                val viewModel: MangaReaderViewModel = viewModel()
                MangaReaderScreen(
                    viewModel = viewModel,
                    mangaId = mangaId,
                    chapterId = chapterId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "$ROUTE_DETAILS/{slug}",
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                val viewModel: DetailsViewModel = viewModel()
                DetailsScreen(
                    viewModel = viewModel,
                    slug = slug,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AnimeTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ANI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = AccentPink,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = "MPV",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(10.dp))
        Spacer(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(
                    brush = Brush.horizontalGradient(listOf(AccentPink, AccentViolet)),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(10.dp))
        IconButtonLike(
            icon = Icons.Filled.Settings,
            description = "Player settings",
            onClick = onSettings,
        )
    }
}

@Composable
private fun IconButtonLike(icon: ImageVector, description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AnimeBottomBar(
    currentRoute: String?,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onManga: () -> Unit,
    onFiles: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationBar(
        containerColor = Color(0xFF101018),
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = currentRoute == ROUTE_HOME,
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") },
            colors = navigationColors(),
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_SEARCH,
            onClick = onSearch,
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text("Search") },
            colors = navigationColors(),
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_LIBRARY,
            onClick = onLibrary,
            icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
            label = { Text("My List") },
            colors = navigationColors(),
        )
        NavigationBarItem(
            selected = currentRoute == ROUTE_MANGA,
            onClick = onManga,
            icon = { Icon(Icons.Filled.Book, contentDescription = null) },
            label = { Text("Manga") },
            colors = navigationColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onFiles,
            icon = { Icon(Icons.Filled.List, contentDescription = null) },
            label = { Text("Files") },
            colors = navigationColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") },
            colors = navigationColors(),
        )
    }
}

@Composable
private fun navigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AccentPink,
    selectedTextColor = AccentPink,
    indicatorColor = Color(0x22FF4D6D),
    unselectedIconColor = Color(0xFF8A8A9E),
    unselectedTextColor = Color(0xFF8A8A9E),
)
