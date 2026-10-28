package `is`.xyz.mpv.anime.manga

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import `is`.xyz.mpv.anime.AccentPink
import `is`.xyz.mpv.anime.ErrorState
import `is`.xyz.mpv.anime.Library
import `is`.xyz.mpv.anime.LoadingState
import `is`.xyz.mpv.anime.mangaImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MangaReaderScreen(
    viewModel: MangaReaderViewModel,
    mangaId: String,
    chapterId: String,
    onBack: () -> Unit,
) {
    LaunchedEffect(mangaId, chapterId) {
        viewModel.loadChapter(mangaId, chapterId)
    }

    when (val state = viewModel.state) {
        is MangaReaderUiState.Loading -> LoadingState()
        is MangaReaderUiState.Error -> ErrorState(state.message) { viewModel.loadChapter(mangaId, chapterId) }
        is MangaReaderUiState.Content -> MangaReaderContent(
            mangaId = mangaId,
            state = state,
            onPrevChapter = viewModel::prevChapter,
            onNextChapter = viewModel::nextChapter,
            onOpenChapter = viewModel::openChapter,
            onRetry = viewModel::retry,
            onBack = onBack,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaReaderContent(
    mangaId: String,
    state: MangaReaderUiState.Content,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenChapter: (MangaChapter) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var showChrome by rememberSaveable { mutableStateOf(true) }
    var pagedMode by rememberSaveable { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showExternalDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pages = state.pages
    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = (state.initialPage - 1).coerceAtLeast(0),
        pageCount = { pages.size.coerceAtLeast(1) },
    )

    val currentPage = if (pagedMode) {
        (pagerState.currentPage + 1).coerceIn(1, pages.size.coerceAtLeast(1))
    } else {
        (listState.firstVisibleItemIndex + 1).coerceIn(1, pages.size.coerceAtLeast(1))
    }

    // Keep the screen awake while reading.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Immersive mode: hide the system bars while the chrome is hidden.
    LaunchedEffect(showChrome) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (showChrome) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Restore the saved page the first time a chapter is shown.
    var restoredChapterId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.chapter.id, pages.size) {
        if (pages.isEmpty()) return@LaunchedEffect
        val target = (state.initialPage - 1).coerceIn(0, pages.lastIndex)
        listState.scrollToItem(target)
        pagerState.scrollToPage(target)
        restoredChapterId = state.chapter.id
    }

    // Persist reading progress while scrolling (debounced). Wait until the saved page
    // has been restored so opening a chapter doesn't reset progress to page 1.
    LaunchedEffect(currentPage, state.chapter.id, pages.size, restoredChapterId) {
        if (pages.isEmpty() || restoredChapterId != state.chapter.id) return@LaunchedEffect
        delay(700)
        Library.rememberMangaChapter(
            mangaId = mangaId,
            title = state.mangaTitle,
            coverUrl = state.coverUrl,
            chapterId = state.chapter.id,
            chapterNumber = state.chapter.chapterNumber,
            page = currentPage,
            totalPages = pages.size,
        )
    }

    val openInBrowser = {
        val url = MangaApi.chapterWebUrl(state.mangaSlug, state.chapter.id, state.chapter.chapterNumber)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showChrome = !showChrome })
            },
    ) {
        when {
            pages.isEmpty() -> EmptyPagesCard(
                chapter = state.chapter,
                onRetry = onRetry,
                onOpenInBrowser = { showExternalDialog = true },
                onPrev = if (state.hasPrev) onPrevChapter else null,
                onNext = if (state.hasNext) onNextChapter else null,
                onBack = onBack,
            )

            pagedMode -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = mangaImageRequest(pages[index].url),
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = if (showChrome) 68.dp else 6.dp,
                    bottom = if (showChrome) 132.dp else 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = pages,
                    key = { index, page -> "${page.pageNumber}_$index" },
                ) { _, page ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = mangaImageRequest(page.url),
                            contentDescription = "Page ${page.pageNumber}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item(key = "end_of_chapter") {
                    EndOfChapterCard(
                        hasNext = state.hasNext,
                        nextChapter = state.chapters.getOrNull(state.index + 1),
                        onNext = onNextChapter,
                        onBack = onBack,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = state.mangaTitle,
                chapter = state.chapter,
                pageCount = pages.size,
                isBookmarked = Library.isMangaBookmarked(mangaId),
                onBack = onBack,
                onToggleBookmark = {
                    Library.toggleMangaBookmark(mangaId, state.mangaTitle, state.coverUrl)
                },
                onOpenChapters = { showChapters = true },
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                currentPage = currentPage,
                pageCount = pages.size,
                pagedMode = pagedMode,
                hasPrev = state.hasPrev,
                hasNext = state.hasNext,
                onPrevChapter = onPrevChapter,
                onNextChapter = onNextChapter,
                onToggleMode = {
                    val page = currentPage
                    pagedMode = !pagedMode
                    scope.launch {
                        if (pagedMode) {
                            pagerState.scrollToPage((page - 1).coerceAtLeast(0))
                        } else {
                            listState.scrollToItem((page - 1).coerceAtLeast(0))
                        }
                    }
                },
                onOpenChapters = { showChapters = true },
                onSeek = { target ->
                    scope.launch {
                        val index = (target - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        if (pagedMode) pagerState.animateScrollToPage(index)
                        else listState.animateScrollToItem(index)
                    }
                },
            )
        }
    }

    if (showChapters) {
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            sheetState = sheetState,
            containerColor = Color(0xFF12121C),
            contentColor = Color.White,
        ) {
            ChapterListSheet(
                mangaTitle = state.mangaTitle,
                chapters = state.chapters,
                currentChapterId = state.chapter.id,
                readUpTo = Library.mangaReadUpTo(mangaId),
                onChapterClick = { chapter ->
                    showChapters = false
                    onOpenChapter(chapter)
                },
            )
        }
    }

    if (showExternalDialog) {
        AlertDialog(
            onDismissRequest = { showExternalDialog = false },
            containerColor = Color(0xFF1A1A26),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            title = { Text("Read on Comick") },
            text = {
                Text(
                    "This upload is hosted on an external site, so its pages can't be shown " +
                        "inside the app. Open the chapter in your browser instead?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExternalDialog = false
                    openInBrowser()
                }) {
                    Text("Open browser", color = AccentPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExternalDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: MangaChapter,
    pageCount: Int,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenChapters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20D0D14))
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append("Chapter ${chapter.chapterNumber}")
                    chapter.group?.let { append(" · $it") }
                    if (pageCount > 0) append(" · $pageCount pages")
                },
                color = Color(0xFF9E9EB3),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = if (isBookmarked) AccentPink else Color.White,
            )
        }
        IconButton(onClick = onOpenChapters) {
            Icon(Icons.Filled.List, contentDescription = "Chapters", tint = Color.White)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    pagedMode: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleMode: () -> Unit,
    onOpenChapters: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    var sliderValue by remember(currentPage, pageCount) {
        mutableFloatStateOf(currentPage.toFloat())
    }
    val maxPage = pageCount.coerceAtLeast(2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20D0D14))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (pageCount > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$currentPage", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = sliderValue.coerceIn(1f, maxPage.toFloat()),
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onSeek(sliderValue.roundToInt()) },
                    valueRange = 1f..maxPage.toFloat(),
                    steps = (pageCount - 2).coerceAtLeast(0),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPink,
                        activeTrackColor = AccentPink,
                        inactiveTrackColor = Color(0xFF33334A),
                    ),
                )
                Text("$pageCount", color = Color(0xFF9E9EB3), fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevChapter, enabled = hasPrev) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Previous chapter",
                        tint = if (hasPrev) Color.White else Color(0xFF4A4A5E),
                    )
                }
                Text(
                    text = if (pagedMode) "Paged" else "Webtoon",
                    color = Color(0xFF9E9EB3),
                    fontSize = 11.sp,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleMode) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = if (pagedMode) "Switch to webtoon mode" else "Switch to paged mode",
                        tint = AccentPink,
                    )
                }
                IconButton(onClick = onOpenChapters) {
                    Icon(Icons.Filled.List, contentDescription = "Chapter list", tint = Color.White)
                }
                IconButton(onClick = onNextChapter, enabled = hasNext) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Next chapter",
                        tint = if (hasNext) AccentPink else Color(0xFF4A4A5E),
                    )
                }
            }
        }
    }
}

@Composable
private fun EndOfChapterCard(
    hasNext: Boolean,
    nextChapter: MangaChapter?,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161624)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (hasNext) "End of chapter" else "You're all caught up",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasNext) {
                    nextChapter?.let { "Next: ${it.displayName}" } ?: "Next chapter"
                } else {
                    "No more chapters uploaded yet."
                },
                color = Color(0xFF9E9EB3),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            if (hasNext) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Read next chapter", color = Color.White, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26263A)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Back to details", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyPagesCard(
    chapter: MangaChapter,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161624)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = AccentPink,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "No pages for chapter ${chapter.chapterNumber}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "This upload${chapter.group?.let { " by $it" } ?: ""} is hosted on an " +
                        "external site and no alternate source had readable pages. Try another " +
                        "upload of the same chapter, or open it in the browser.",
                    color = Color(0xFF9E9EB3),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try other uploads", color = Color.White, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenInBrowser) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = Color(0xFF9E9EB3), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open browser", color = Color(0xFF9E9EB3), fontSize = 12.sp)
                    }
                    TextButton(onClick = onBack) {
                        Text("Back", color = Color(0xFF9E9EB3), fontSize = 12.sp)
                    }
                }
                if (onPrev != null || onNext != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onPrev?.let {
                            TextButton(onClick = it) {
                                Text("‹ Prev chapter", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        onNext?.let {
                            TextButton(onClick = it) {
                                Text("Next chapter ›", color = AccentPink, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterListSheet(
    mangaTitle: String,
    chapters: List<MangaChapter>,
    currentChapterId: String,
    readUpTo: Double?,
    onChapterClick: (MangaChapter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = mangaTitle,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${chapters.size} chapters",
            color = Color(0xFF9E9EB3),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(chapters, key = { it.id }) { chapter ->
                val isCurrent = chapter.id == currentChapterId
                val isRead = readUpTo != null && chapter.numberValue <= readUpTo && !isCurrent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isCurrent -> Color(0x33FF4D6D)
                                else -> Color(0xFF1A1A28)
                            },
                        )
                        .clickable { onChapterClick(chapter) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chapter.displayName,
                            color = if (isCurrent) AccentPink else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = buildString {
                                chapter.group?.let { append(it) }
                                chapter.publishDate?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append(it)
                                }
                                if (chapter.external) {
                                    if (isNotEmpty()) append(" · ")
                                    append("external")
                                }
                            },
                            color = Color(0xFF8A8A9E),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when {
                        isCurrent -> Text("READING", color = AccentPink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        isRead -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Read",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
