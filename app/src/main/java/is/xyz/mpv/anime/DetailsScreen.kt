package `is`.xyz.mpv.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
    slug: String,
    onBack: () -> Unit,
) {
    LaunchedEffect(slug) { viewModel.load(slug) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var resolvingEpisode by remember { mutableStateOf<String?>(null) }
    var downloadEpisode by remember { mutableStateOf<Episode?>(null) }
    var preparingDownload by remember { mutableStateOf<String?>(null) }
    var pendingError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingError) {
        pendingError?.let {
            snackbarHostState.showSnackbar(it)
            pendingError = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = viewModel.state) {
            is DetailsUiState.Loading -> LoadingState()
            is DetailsUiState.Error -> ErrorState(state.message) { viewModel.load(slug) }
            is DetailsUiState.Content -> DetailsContent(
                details = state.details,
                episodes = state.episodes,
                mode = viewModel.mode,
                onModeChange = viewModel::changeMode,
                onBack = onBack,
                resolvingEpisode = resolvingEpisode,
                preparingDownload = preparingDownload,
                onBookmarkClick = {
                    Library.toggleBookmark(state.details.slug, state.details.title, state.details.cover)
                },
                onDownloadClick = { episode ->
                    val existing = Library.state.downloads
                        .firstOrNull { it.episodeId == episode.id && it.mode == viewModel.mode }
                    when {
                        existing?.isActive == true ->
                            pendingError = "Episode ${episode.number} is already downloading (${existing.progress}%)"
                        existing?.isComplete == true ->
                            pendingError = "Episode ${episode.number} is already available offline"
                        else -> downloadEpisode = episode
                    }
                },
                onEpisodeClick = { episode ->
                    if (resolvingEpisode == null) {
                        resolvingEpisode = episode.id
                        scope.launch {
                            try {
                                val stream = viewModel.resolve(episode.id)
                                Library.rememberEpisode(state.details, episode, viewModel.mode)
                                launchStream(
                                    context = context,
                                    title = state.details.title,
                                    episodeNumber = episode.number,
                                    episodeId = episode.id,
                                    stream = stream,
                                    startMs = resumePositionFor(episode.id),
                                )
                            } catch (t: Throwable) {
                                pendingError = t.userMessage()
                            } finally {
                                resolvingEpisode = null
                            }
                        }
                    }
                },
            )
        }

        val content = viewModel.state as? DetailsUiState.Content
        val target = downloadEpisode
        if (content != null && target != null) {
            val details = content.details
            QualityDialog(
                episodeNumber = target.number,
                onDismiss = { downloadEpisode = null },
                onSelect = { quality ->
                    downloadEpisode = null
                    preparingDownload = target.id
                    scope.launch {
                        try {
                            val stream = viewModel.resolve(target.id)
                            DownloadService.enqueue(
                                context = context,
                                details = details,
                                episode = target,
                                mode = viewModel.mode,
                                quality = quality,
                                stream = stream,
                            )
                            Library.rememberEpisode(details, target, viewModel.mode)
                            pendingError = "Downloading episode ${target.number} in ${quality}p"
                        } catch (t: Throwable) {
                            pendingError = t.userMessage()
                        } finally {
                            preparingDownload = null
                        }
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsContent(
    details: AnimeDetails,
    episodes: List<Episode>,
    mode: String,
    onModeChange: (String) -> Unit,
    onBack: () -> Unit,
    resolvingEpisode: String?,
    preparingDownload: String?,
    onBookmarkClick: () -> Unit,
    onDownloadClick: (Episode) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
) {
    var expanded by remember(details.slug) { mutableStateOf(false) }
    val bookmarked = Library.isBookmarked(details.slug)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Hero(details, onBack)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(
                details = details,
                episodes = episodes,
                bookmarked = bookmarked,
                onBookmarkClick = onBookmarkClick,
            )
        }

        if (!details.description.isNullOrBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SectionTitle("Synopsis")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = details.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (details.description.length > 220) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(
                                text = if (expanded) "Show less" else "Read more",
                                color = AccentPink,
                            )
                        }
                    }
                }
            }
        }

        if (details.genres.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    details.genres.forEach { genre -> GenreChip(genre) }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(
                    text = "Episodes",
                    modifier = Modifier.weight(1f),
                )
                if (details.dubCount > 0) {
                    FilterChip(
                        selected = mode == "sub",
                        onClick = { onModeChange("sub") },
                        label = { Text("SUB") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = mode == "dub",
                        onClick = { onModeChange("dub") },
                        label = { Text("DUB") },
                    )
                }
            }
        }

        items(episodes, key = { it.id }) { episode ->
            val download = Library.state.downloads.firstOrNull {
                it.episodeId == episode.id && it.mode == mode
            }
            EpisodeCell(
                episode = episode,
                loading = resolvingEpisode == episode.id,
                download = download,
                preparing = preparingDownload == episode.id,
                onClick = { onEpisodeClick(episode) },
                onDownloadClick = { onDownloadClick(episode) },
            )
        }
    }
}

@Composable
private fun Hero(details: AnimeDetails, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
    ) {
        AsyncImage(
            model = details.banner ?: details.cover,
            contentDescription = details.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x55000000),
                        0.55f to Color(0x660B0B12),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color(0x88000000)),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
private fun Header(
    details: AnimeDetails,
    episodes: List<Episode>,
    bookmarked: Boolean,
    onBookmarkClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        AsyncImage(
            model = details.cover,
            contentDescription = details.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(108.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = details.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                details.score?.let { ScoreBadge(it) }
                details.format?.let { Pill(it) }
                details.year?.let { Pill(it.toString()) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${episodes.size} episodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (details.subCount > 0 || details.dubCount > 0) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (details.subCount > 0) Tag("SUB ${details.subCount}", SubCyan)
                    if (details.dubCount > 0) Tag("DUB ${details.dubCount}", DubAmber)
                }
            }
        }
        IconButton(onClick = onBookmarkClick) {
            Icon(
                imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (bookmarked) "Remove from watchlist" else "Add to watchlist",
                tint = if (bookmarked) AccentPink else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreBadge(score: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x3322D3EE))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = Color(0xFFFFC94D),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = String.format(Locale.US, "%.1f", score / 10.0),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8FE9F7),
        )
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GenreChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EpisodeCell(
    episode: Episode,
    loading: Boolean,
    download: DownloadItem?,
    preparing: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !loading) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AccentPink,
            )
        } else {
            Text(
                text = episode.number,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !preparing) { onDownloadClick() },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preparing -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = AccentPink,
                    )

                    download?.isComplete == true -> Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = SubCyan,
                        modifier = Modifier.size(15.dp),
                    )

                    download?.isActive == true -> CircularProgressIndicator(
                        progress = { (download?.progress ?: 0) / 100f },
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 1.5.dp,
                        color = AccentPink,
                    )

                    else -> Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download episode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityDialog(
    episodeNumber: String,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download episode $episodeNumber") },
        text = {
            Column {
                Text(
                    text = "Choose a quality. Higher looks better and uses more storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                    480 to "480p · small",
                    720 to "720p · balanced",
                    1080 to "1080p · best",
                ).forEach { (quality, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(quality) }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = AccentPink,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
