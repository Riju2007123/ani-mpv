package `is`.xyz.mpv.anime.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import `is`.xyz.mpv.anime.AccentPink
import `is`.xyz.mpv.anime.ErrorState
import `is`.xyz.mpv.anime.Library
import `is`.xyz.mpv.anime.LoadingState
import `is`.xyz.mpv.anime.SectionTitle
import `is`.xyz.mpv.anime.mangaImageRequest

@Composable
fun MangaDetailsScreen(
    viewModel: MangaDetailsViewModel,
    mangaId: String,
    onBack: () -> Unit,
    onChapterClick: (MangaChapter) -> Unit,
) {
    LaunchedEffect(mangaId) {
        viewModel.load(mangaId)
    }

    when (val state = viewModel.state) {
        is MangaDetailsUiState.Loading -> LoadingState()
        is MangaDetailsUiState.Error -> ErrorState(state.message) { viewModel.load(mangaId, forceRefresh = true) }
        is MangaDetailsUiState.Content -> MangaDetailsContent(
            details = state.details,
            chapters = state.chapters,
            onBack = onBack,
            onChapterClick = onChapterClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaDetailsContent(
    details: MangaDetails,
    chapters: List<MangaChapter>,
    onBack: () -> Unit,
    onChapterClick: (MangaChapter) -> Unit,
) {
    val history = Library.mangaHistoryFor(details.id)
    val readUpTo = history?.chapterNumber?.toDoubleOrNull()
    val continueChapter = history?.let { item ->
        chapters.firstOrNull { it.id == item.chapterId }
            ?: chapters.firstOrNull { it.chapterNumber == item.chapterNumber }
    }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var synopsisExpanded by rememberSaveable { mutableStateOf(false) }

    val orderedChapters = remember(chapters, newestFirst) {
        if (newestFirst) chapters.asReversed() else chapters
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            MangaHeaderHero(details, onBack)
        }

        item(key = "actions") {
            MangaActionRow(
                details = details,
                continueChapter = continueChapter,
                resumePage = history?.pageNumber,
                firstChapter = chapters.firstOrNull(),
                latestChapter = chapters.lastOrNull(),
                hasChapters = chapters.isNotEmpty(),
                onChapterClick = onChapterClick,
            )
        }

        item(key = "stats") {
            MangaStatsRow(details, chapters.size)
        }

        if (!details.genres.isNullOrEmpty()) {
            item(key = "genres") {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    details.genres.forEach { genre ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E2E))
                                .border(1.dp, Color(0xFF2E2E44), RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(genre, color = Color(0xFFC9C9DA), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        details.description?.let { desc ->
            item(key = "synopsis") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Synopsis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = if (synopsisExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (desc.length > 260) {
                        TextButton(
                            onClick = { synopsisExpanded = !synopsisExpanded },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (synopsisExpanded) "Show less" else "Read more",
                                color = AccentPink,
                                fontSize = 12.sp,
                            )
                            Icon(
                                imageVector = if (synopsisExpanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = AccentPink,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        item(key = "chapters_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Book,
                    contentDescription = null,
                    tint = AccentPink,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                SectionTitle("Chapters (${chapters.size})")
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { newestFirst = !newestFirst }) {
                    Icon(
                        imageVector = if (newestFirst) {
                            Icons.Filled.KeyboardArrowDown
                        } else {
                            Icons.Filled.KeyboardArrowUp
                        },
                        contentDescription = null,
                        tint = AccentPink,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (newestFirst) "Newest" else "Oldest",
                        color = AccentPink,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (chapters.isEmpty()) {
            item(key = "no_chapters") {
                Text(
                    text = "No English chapters available yet.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        items(orderedChapters, key = { it.id }) { chapter ->
            val isCurrent = chapter.id == history?.chapterId
            val isRead = readUpTo != null && chapter.numberValue <= readUpTo && !isCurrent
            MangaChapterRow(
                chapter = chapter,
                isRead = isRead,
                isCurrent = isCurrent,
                onClick = onChapterClick,
            )
        }
    }
}

@Composable
private fun MangaActionRow(
    details: MangaDetails,
    continueChapter: MangaChapter?,
    resumePage: Int?,
    firstChapter: MangaChapter?,
    latestChapter: MangaChapter?,
    hasChapters: Boolean,
    onChapterClick: (MangaChapter) -> Unit,
) {
    val primaryChapter = continueChapter ?: firstChapter
    val primaryLabel = when {
        continueChapter != null -> {
            val page = resumePage ?: 1
            "Continue · Ch ${continueChapter.chapterNumber}" + if (page > 1) " · p.$page" else ""
        }
        firstChapter != null -> "Start reading · Ch ${firstChapter.chapterNumber}"
        else -> "No chapters yet"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { primaryChapter?.let(onChapterClick) },
            enabled = primaryChapter != null,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPink,
                disabledContainerColor = Color(0xFF2A2A3C),
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = primaryLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (hasChapters && latestChapter != null && latestChapter.id != primaryChapter?.id) {
            OutlinedButton(
                onClick = { onChapterClick(latestChapter) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPink),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("Latest", color = AccentPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        val isBookmarked = Library.isMangaBookmarked(details.id)
        IconButton(
            onClick = { Library.toggleMangaBookmark(details.id, details.title, details.coverUrl) },
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E2E)),
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                tint = if (isBookmarked) AccentPink else Color.White,
            )
        }
    }
}

@Composable
private fun MangaStatsRow(details: MangaDetails, chapterCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        details.rating?.let { rating ->
            StatChip {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(String.format("%.1f", rating), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        details.year?.let { year ->
            StatChip { Text("$year", color = Color(0xFFC9C9DA), fontSize = 11.sp) }
        }
        details.status?.let { status ->
            StatChip {
                Text(
                    text = status.replaceFirstChar { it.uppercase() },
                    color = if (status == "ongoing") Color(0xFF34D399) else Color(0xFF93A5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        details.originLabel?.let { label ->
            StatChip { Text(label, color = Color(0xFFC9C9DA), fontSize = 11.sp) }
        }
        if (chapterCount > 0) {
            StatChip { Text("$chapterCount ch", color = Color(0xFFC9C9DA), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun StatChip(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A28))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun MangaHeaderHero(details: MangaDetails, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        if (!details.coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = mangaImageRequest(details.coverUrl),
                contentDescription = details.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x88000000),
                        0.45f to Color(0xCC0D0D14),
                        1f to Color(0xFF0D0D14),
                    ),
                ),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        val isBookmarked = Library.isMangaBookmarked(details.id)
        IconButton(
            onClick = { Library.toggleMangaBookmark(details.id, details.title, details.coverUrl) },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Bookmark Manga",
                tint = if (isBookmarked) AccentPink else Color.White,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Card(
                modifier = Modifier
                    .width(104.dp)
                    .height(156.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!details.coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = mangaImageRequest(details.coverUrl),
                        contentDescription = details.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = details.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                details.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentPink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                details.status?.let { status ->
                    Text(
                        text = status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaChapterRow(
    chapter: MangaChapter,
    isRead: Boolean,
    isCurrent: Boolean,
    onClick: (MangaChapter) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onClick(chapter) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> Color(0x33FF4D6D)
                else -> Color(0xFF161624)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRead) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Read",
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Book,
                    contentDescription = null,
                    tint = if (isCurrent) AccentPink else Color(0xFF5A5A72),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) AccentPink else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    chapter.group?.let { append(it) }
                    chapter.publishDate?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (chapter.external) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF2A2A3C))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("EXT", color = Color(0xFF9E9EB3), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
