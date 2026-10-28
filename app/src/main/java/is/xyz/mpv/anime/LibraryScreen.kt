package `is`.xyz.mpv.anime

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "My List": bookmarked anime, continue-watching history and offline
 * downloads. Everything here works without a network except resuming a
 * stream that was never downloaded.
 */
@Composable
fun LibraryScreen(
    onOpenDetails: (String) -> Unit,
    onOpenMangaDetails: (String) -> Unit = {},
    onOpenMangaReader: (mangaId: String, chapterId: String, chapterNumber: String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = Library.state

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var busyEpisode by remember { mutableStateOf<String?>(null) }

    fun resume(item: HistoryItem) {
        if (busyEpisode != null) return
        busyEpisode = item.episodeId
        scope.launch {
            try {
                resumeFromHistory(context, item)
            } catch (t: Throwable) {
                Toast.makeText(context, t.userMessage(), Toast.LENGTH_LONG).show()
            } finally {
                busyEpisode = null
            }
        }
    }

    fun retry(item: DownloadItem) {
        if (busyEpisode != null) return
        busyEpisode = item.episodeId
        scope.launch {
            try {
                val stream = AnimeRepository.resolve(item.episodeId, item.mode)
                DownloadService.enqueue(
                    context = context,
                    details = item.toDetails(),
                    episode = Episode(item.episodeNumber, item.episodeId, null),
                    mode = item.mode,
                    quality = item.quality,
                    stream = stream,
                )
            } catch (t: Throwable) {
                Toast.makeText(context, t.userMessage(), Toast.LENGTH_LONG).show()
            } finally {
                busyEpisode = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = AccentPink,
        ) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Watchlist") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("History") },
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { Text("Downloads") },
            )
        }

        when (tab) {
            0 -> if (state.watchlist.isEmpty() && state.mangaWatchlist.isEmpty()) {
                EmptyState("Nothing bookmarked yet.\nTap the bookmark on any anime or manga to save it here.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.watchlist.isNotEmpty()) {
                        item(key = "anime_watchlist_header") {
                            SectionTitle("Anime Watchlist")
                        }
                        item(key = "anime_watchlist_grid") {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(104.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((180 * ((state.watchlist.size + 2) / 3)).dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(state.watchlist, key = { it.slug }) { item ->
                                    WatchCard(
                                        item = item,
                                        onClick = { onOpenDetails(item.slug) },
                                        onRemove = { Library.removeFromWatchlist(item.slug) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.mangaWatchlist.isNotEmpty()) {
                        item(key = "manga_watchlist_header") {
                            SectionTitle("Manga Watchlist")
                        }
                        item(key = "manga_watchlist_grid") {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(104.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((180 * ((state.mangaWatchlist.size + 2) / 3)).dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(state.mangaWatchlist, key = { it.id }) { item ->
                                    MangaWatchCard(
                                        item = item,
                                        onClick = { onOpenMangaDetails(item.id) },
                                        onRemove = { Library.removeMangaFromWatchlist(item.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> if (state.history.isEmpty() && state.mangaHistory.isEmpty()) {
                EmptyState("Your watch and reading history will appear here once you play or read something.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.mangaHistory.isNotEmpty()) {
                        item(key = "manga_history_header") {
                            SectionTitle("Continue Reading Manga")
                        }
                        items(state.mangaHistory, key = { "manga_${it.mangaId}" }) { item ->
                            MangaHistoryRow(
                                item = item,
                                onClick = { onOpenMangaReader(item.mangaId, item.chapterId, item.chapterNumber) },
                                onDelete = { Library.removeMangaHistory(item.mangaId) },
                            )
                        }
                    }

                    if (state.history.isNotEmpty()) {
                        item(key = "anime_history_header") {
                            SectionTitle("Continue Watching Anime")
                        }
                        items(state.history, key = { it.episodeId }) { item ->
                            HistoryRow(
                                item = item,
                                resuming = busyEpisode == item.episodeId,
                                onClick = { resume(item) },
                                onDelete = { Library.removeHistory(item.episodeId) },
                            )
                        }
                    }
                }
            }

            else -> if (state.downloads.isEmpty()) {
                EmptyState("No offline episodes yet.\nTap the download arrow on an episode.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.downloads, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            busy = busyEpisode == item.episodeId,
                            onPlay = {
                                launchLocalDownload(
                                    context = context,
                                    item = item,
                                    startMs = resumePositionFor(item.episodeId),
                                )
                            },
                            onCancel = { DownloadService.cancel(context, item.id) },
                            onRetry = { retry(item) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = {
                Text("Episode ${item.episodeNumber} of ${item.title} will be removed from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Library.removeDownload(item.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete", color = AccentPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun WatchCard(item: WatchItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() },
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove bookmark",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    resuming: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !resuming) { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B1B26)),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (resuming) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                    color = AccentPink,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Episode ${item.episodeNumber} · ${item.mode.uppercase(Locale.US)} · " +
                    "${(item.progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape),
                color = AccentPink,
                trackColor = Color(0x33FFFFFF),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove from history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    busy: Boolean,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = item.isComplete) { onPlay() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B1B26)),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.isComplete) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000)),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Episode ${item.episodeNumber} · ${item.mode.uppercase(Locale.US)} · ${item.quality}p",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            when {
                item.isActive -> {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape),
                        color = AccentPink,
                        trackColor = Color(0x33FFFFFF),
                    )
                    Text(
                        text = "Downloading ${item.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                item.isComplete -> Text(
                    text = "${formatBytes(item.bytes)} · Available offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = SubCyan,
                )

                item.status == DownloadStatus.FAILED -> Text(
                    text = item.error ?: "Download failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6B81),
                )

                else -> Text(
                    text = "Canceled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .padding(6.dp),
                strokeWidth = 2.dp,
                color = AccentPink,
            )

            item.isComplete -> IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = AccentPink)
            }

            item.isActive -> IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Retry download",
                    tint = AccentPink,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MangaWatchCard(item: MangaWatchItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .width(108.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = mangaImageRequest(item.coverUrl),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color(0x99000000),
                        1f to Color(0xF0000000),
                    ),
                ),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )
    }
}

@Composable
private fun MangaHistoryRow(item: MangaHistoryItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1B1B26)),
        ) {
            if (!item.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = mangaImageRequest(item.coverUrl),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Chapter ${item.chapterNumber} · page ${item.pageNumber}/${item.totalPages}",
                style = MaterialTheme.typography.bodySmall,
                color = AccentPink,
            )
            Spacer(Modifier.height(6.dp))
            val progress = if (item.totalPages > 0) {
                (item.pageNumber.toFloat() / item.totalPages).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentPink,
                trackColor = Color(0xFF2A2A3C),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove from history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun DownloadItem.toDetails() = AnimeDetails(
    slug = slug,
    title = title,
    animeId = animeId,
    cover = cover,
    banner = null,
    description = null,
    genres = emptyList(),
    score = null,
    year = null,
    format = null,
    totalEpisodes = null,
    subCount = 0,
    dubCount = 0,
)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}
