package `is`.xyz.mpv.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (AnimeCard) -> Unit,
    onContinueWatching: (HistoryItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        GenreFilterBar(
            selectedGenre = viewModel.selectedGenre,
            onSelectGenre = viewModel::selectGenre,
        )

        when (val state = viewModel.state) {
            is HomeUiState.Loading -> LoadingState()
            is HomeUiState.Error -> ErrorState(state.message) { viewModel.load() }
            is HomeUiState.Content -> HomeContent(
                data = state.data,
                onAnimeClick = onAnimeClick,
                onContinueWatching = onContinueWatching,
                onLoadMore = viewModel::loadNextGenreRow,
            )
        }
    }
}

@Composable
private fun GenreFilterBar(
    selectedGenre: String,
    onSelectGenre: (String) -> Unit,
) {
    val genres = listOf(
        "all" to "All",
        "popular" to "Most Popular",
        "top-airing" to "Top Airing",
        "action" to "Action",
        "fantasy" to "Fantasy",
        "romance" to "Romance",
        "supernatural" to "Supernatural",
        "comedy" to "Comedy",
        "movie" to "Movies",
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D14)),
    ) {
        items(genres) { (slug, label) ->
            val isSelected = selectedGenre == slug
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) AccentPink else Color(0xFF1E1E2A))
                    .clickable { onSelectGenre(slug) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFFB0B0C0),
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onAnimeClick: (AnimeCard) -> Unit,
    onContinueWatching: (HistoryItem) -> Unit,
    onLoadMore: () -> Unit,
) {
    val resumable = Library.state.history.filterNot { it.isFinished }.take(12)
    val listState = rememberLazyListState()

    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    val totalItems = listState.layoutInfo.totalItemsCount
    LaunchedEffect(lastVisibleIndex, totalItems) {
        if (totalItems > 0 && lastVisibleIndex >= totalItems - 2) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
    ) {
        if (data.spotlight.isNotEmpty()) {
            item(key = "spotlight") {
                Spotlight(data.spotlight, onAnimeClick)
            }
        }
        if (resumable.isNotEmpty()) {
            item(key = "continue") {
                ContinueWatchingRow(resumable, onContinueWatching)
            }
        }
        data.rows.forEach { row ->
            item(key = "row-${row.title}") {
                SectionRow(row, onAnimeClick)
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(items: List<HistoryItem>, onClick: (HistoryItem) -> Unit) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionTitle(
            text = "Continue watching",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.episodeId }) { item ->
                Column(
                    modifier = Modifier
                        .width(168.dp)
                        .clickable { onClick(item) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(94.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = item.cover,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xCC000000)),
                                    ),
                                ),
                        )
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
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                            color = AccentPink,
                            trackColor = Color(0x55FFFFFF),
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = "Episode ${item.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Spotlight(items: List<AnimeCard>, onAnimeClick: (AnimeCard) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            SpotlightCard(items[page], onAnimeClick)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(items.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 18.dp else 6.dp, 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) AccentPink else Color(0x55FFFFFF)),
                )
            }
        }
    }
}

@Composable
private fun SpotlightCard(card: AnimeCard, onAnimeClick: (AnimeCard) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onAnimeClick(card) },
    ) {
        AsyncImage(
            model = card.banner ?: card.cover,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x33000000),
                        0.45f to Color(0x990B0B12),
                        1f to Color(0xF20B0B12),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            card.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onAnimeClick(card) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Watch now")
            }
        }
    }
}

@Composable
private fun SectionRow(row: HomeRow, onAnimeClick: (AnimeCard) -> Unit) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionTitle(
            text = row.title,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(row.items, key = { it.slug }) { card ->
                PosterCard(card = card, onClick = onAnimeClick)
            }
        }
    }
}