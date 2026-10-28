package `is`.xyz.mpv.anime.manga

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import `is`.xyz.mpv.anime.AccentPink
import `is`.xyz.mpv.anime.ErrorState
import `is`.xyz.mpv.anime.Library
import `is`.xyz.mpv.anime.LoadingState
import `is`.xyz.mpv.anime.MangaHistoryItem
import `is`.xyz.mpv.anime.SectionTitle
import `is`.xyz.mpv.anime.mangaImageRequest
import java.util.Locale

data class TagGroup(val category: String, val tags: List<Pair<String, String>>)

val COMICK_TAG_GROUPS = listOf(
    TagGroup(
        category = "Format",
        tags = listOf(
            "long-strip" to "Long Strip",
            "full-color" to "Full Color",
            "web-comic" to "Web Comic",
            "adaptation" to "Adaptation",
            "oneshot" to "Oneshot",
            "official-colored" to "Official Colored",
        )
    ),
    TagGroup(
        category = "Genre",
        tags = listOf(
            "action" to "Action",
            "adventure" to "Adventure",
            "comedy" to "Comedy",
            "drama" to "Drama",
            "fantasy" to "Fantasy",
            "horror" to "Horror",
            "isekai" to "Isekai",
            "mystery" to "Mystery",
            "psychological" to "Psychological",
            "romance" to "Romance",
            "sci-fi" to "Sci-Fi",
            "slice-of-life" to "Slice of Life",
            "sports" to "Sports",
            "thriller" to "Thriller",
        )
    ),
    TagGroup(
        category = "Theme",
        tags = listOf(
            "aliens" to "Aliens",
            "cooking" to "Cooking",
            "crossdressing" to "Crossdressing",
            "demons" to "Demons",
            "genderswap" to "Genderswap",
            "harem" to "Harem",
            "magic" to "Magic",
            "martial-arts" to "Martial Arts",
            "military" to "Military",
            "monsters" to "Monsters",
            "reincarnation" to "Reincarnation",
            "reverse-harem" to "Reverse Harem",
            "school-life" to "School Life",
            "supernatural" to "Supernatural",
            "survival" to "Survival",
            "time-travel" to "Time Travel",
            "vampires" to "Vampires",
            "video-games" to "Video Games",
            "villainess" to "Villainess",
            "virtual-reality" to "Virtual Reality",
            "zombies" to "Zombies",
        )
    )
)

@Composable
fun MangaScreen(
    viewModel: MangaHomeViewModel,
    onMangaClick: (MangaCard) -> Unit,
    onContinueReading: (MangaHistoryItem) -> Unit = {},
) {
    var isFilterPanelExpanded by remember { mutableStateOf(false) }
    val history = Library.state.mangaHistory

    Column(modifier = Modifier.fillMaxSize()) {
        MangaHeader(
            query = viewModel.query,
            onQueryChange = viewModel::onQueryChange,
            sort = viewModel.filterState.sort,
            onSortChange = viewModel::updateSort,
            filterCount = viewModel.filterState.selectedTags.size + viewModel.filterState.demographics.size,
            isExpanded = isFilterPanelExpanded,
            onToggleExpand = { isFilterPanelExpanded = !isFilterPanelExpanded },
            isRefreshing = viewModel.isRefreshing,
            onRefresh = viewModel::refresh,
        )

        AnimatedVisibility(visible = isFilterPanelExpanded) {
            ComickFilterPanel(
                filterState = viewModel.filterState,
                onToggleTag = viewModel::toggleTag,
                onToggleDemographic = viewModel::toggleDemographic,
                onSetStatus = viewModel::setStatus,
                onReset = viewModel::resetFilters,
            )
        }

        if (viewModel.isSearching) {
            MangaSearchResults(
                items = viewModel.searchResults,
                currentPage = viewModel.currentPage,
                totalPages = viewModel.totalPages,
                isPageLoading = viewModel.isPageLoading,
                onPageSelect = viewModel::goToPage,
                onMangaClick = onMangaClick,
            )
        } else {
            when (val state = viewModel.state) {
                is MangaHomeUiState.Loading -> LoadingState()
                is MangaHomeUiState.Error -> ErrorState(state.message) { viewModel.loadHome() }
                is MangaHomeUiState.Content -> MangaHomeContent(
                    data = state.data,
                    history = history,
                    onMangaClick = onMangaClick,
                    onContinueReading = onContinueReading,
                )
            }
        }
    }
}

@Composable
private fun MangaHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: String,
    onSortChange: (String) -> Unit,
    filterCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F1A))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search manga...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AccentPink) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color(0xFF222238),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Filter Expand Toggle Button
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isExpanded || filterCount > 0) AccentPink else Color(0xFF1E1E2E)),
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = "Filters",
                    tint = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sort Selector Toggle (Latest / Popular)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E2E))
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sort == "latest") Color(0xFF3B82F6) else Color.Transparent)
                        .clickable { onSortChange("latest") }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text("Latest", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sort == "popular") Color(0xFF3B82F6) else Color.Transparent)
                        .clickable { onSortChange("popular") }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text("Popular", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (filterCount > 0) {
                Text(
                    text = "$filterCount filters active",
                    color = AccentPink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E2E)),
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentPink,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComickFilterPanel(
    filterState: MangaFilterState,
    onToggleTag: (String) -> Unit,
    onToggleDemographic: (String) -> Unit,
    onSetStatus: (String?) -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141424)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose your options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Reset", fontSize = 11.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Demographic Checkboxes
            Text(
                text = "Demographic",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentPink,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("shounen", "shoujo", "seinen", "josei").forEach { demo ->
                    val isChecked = demo in filterState.demographics
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onToggleDemographic(demo) },
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleDemographic(demo) },
                            colors = CheckboxDefaults.colors(checkedColor = AccentPink),
                        )
                        Text(
                            text = demo.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            color = Color.LightGray,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Status",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentPink,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(null to "Any", "ongoing" to "Ongoing", "completed" to "Completed").forEach { (value, label) ->
                    val selected = filterState.status == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AccentPink else Color(0xFF1E1E2E))
                            .clickable { onSetStatus(value) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(label, fontSize = 11.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tags / Genres Grouped
            COMICK_TAG_GROUPS.forEach { group ->
                Text(
                    text = group.category,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPink,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    group.tags.forEach { (tagId, tagLabel) ->
                        val isSelected = tagId in filterState.selectedTags
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF10B981) else Color(0xFF1E1E2E))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF34D399) else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onToggleTag(tagId) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tagLabel,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MangaSearchResults(
    items: List<MangaCard>,
    currentPage: Int,
    totalPages: Int,
    isPageLoading: Boolean,
    onPageSelect: (Int) -> Unit,
    onMangaClick: (MangaCard) -> Unit,
) {
    if (items.isEmpty() && !isPageLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No manga found matching filters", color = Color.Gray)
        }
    } else {
        val gridState = rememberLazyGridState()
        LaunchedEffect(currentPage) {
            if (gridState.firstVisibleItemIndex > 0) gridState.scrollToItem(0)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { card ->
                    MangaPosterCard(card = card, onClick = onMangaClick)
                }
            }

            if (isPageLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentPink,
                    trackColor = Color(0xFF1E1E2E),
                )
            }

            PaginationBar(
                currentPage = currentPage,
                totalPages = totalPages,
                onPageSelect = onPageSelect,
            )
        }
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageSelect: (Int) -> Unit,
) {
    var jumpPageInput by remember(currentPage) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F0F1A))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onPageSelect(1) },
                enabled = currentPage > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Text("<<", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (currentPage > 1) AccentPink else Color.Gray)
            }

            IconButton(
                onClick = { onPageSelect(currentPage - 1) },
                enabled = currentPage > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Text("<", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (currentPage > 1) Color.White else Color.Gray)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentPink)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Page $currentPage" + if (totalPages > 1) " of $totalPages" else "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            IconButton(
                onClick = { onPageSelect(currentPage + 1) },
                enabled = currentPage < totalPages,
                modifier = Modifier.size(32.dp),
            ) {
                Text(">", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (currentPage < totalPages) Color.White else Color.Gray)
            }

            IconButton(
                onClick = { onPageSelect(totalPages) },
                enabled = currentPage < totalPages,
                modifier = Modifier.size(32.dp),
            ) {
                Text(">>", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (currentPage < totalPages) AccentPink else Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Jump to page:", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = jumpPageInput,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) {
                        jumpPageInput = input
                    }
                },
                placeholder = { Text("$currentPage", fontSize = 11.sp, color = Color.DarkGray) },
                modifier = Modifier
                    .width(72.dp)
                    .height(42.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPink,
                    unfocusedBorderColor = Color(0xFF222238),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    val p = jumpPageInput.toIntOrNull()
                    if (p != null && p >= 1) {
                        onPageSelect(p)
                        jumpPageInput = ""
                    }
                }),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val p = jumpPageInput.toIntOrNull()
                    if (p != null && p >= 1) {
                        onPageSelect(p)
                        jumpPageInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Go", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun MangaHomeContent(
    data: MangaHomeData,
    history: List<MangaHistoryItem>,
    onMangaClick: (MangaCard) -> Unit,
    onContinueReading: (MangaHistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        if (history.isNotEmpty()) {
            item(key = "continue_reading") {
                ContinueReadingRow(history, onContinueReading)
            }
        }

        if (data.trending.isNotEmpty()) {
            item(key = "trending") {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    SectionTitle("Trending Manga", Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(data.trending, key = { it.id }) { card ->
                            MangaPosterCard(card = card, onClick = onMangaClick)
                        }
                    }
                }
            }
        }

        if (data.popular.isNotEmpty()) {
            item(key = "popular") {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    SectionTitle("Most Popular", Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(data.popular, key = { it.id }) { card ->
                            MangaPosterCard(card = card, onClick = onMangaClick)
                        }
                    }
                }
            }
        }

        if (data.latest.isNotEmpty()) {
            item(key = "latest") {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    SectionTitle("Latest Uploads", Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(data.latest, key = { it.id }) { card ->
                            MangaPosterCard(card = card, onClick = onMangaClick)
                        }
                    }
                }
            }
        }

        if (data.trending.isEmpty() && data.popular.isEmpty() && data.latest.isEmpty()) {
            item(key = "empty_home") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Couldn't load the manga catalogue. Tap the refresh button.", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingRow(
    items: List<MangaHistoryItem>,
    onContinue: (MangaHistoryItem) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("Continue Reading")
            Spacer(Modifier.weight(1f))
            Text("${items.size}", color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items.take(12), key = { it.mangaId }) { item ->
                ContinueReadingCard(item = item, onClick = { onContinue(item) })
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(item: MangaHistoryItem, onClick: () -> Unit) {
    val progress = if (item.totalPages > 0) {
        (item.pageNumber.toFloat() / item.totalPages).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .width(238.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161624))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
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
            } else {
                Icon(
                    Icons.Filled.Book,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ch ${item.chapterNumber} · page ${item.pageNumber}/${item.totalPages}",
                color = AccentPink,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentPink,
                trackColor = Color(0xFF2A2A3C),
            )
        }
    }
}

@Composable
fun MangaPosterCard(card: MangaCard, onClick: (MangaCard) -> Unit) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable { onClick(card) },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!card.coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = mangaImageRequest(card.coverUrl),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Book, contentDescription = null, tint = Color.Gray)
                    }
                }

                card.status?.let { status ->
                    if (status.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (status == "ongoing") Color(0xFF10B981) else Color(0xFF6366F1))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .align(Alignment.TopStart),
                        ) {
                            Text(
                                text = status.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                card.rating?.let { rating ->
                    Row(
                        modifier = Modifier
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(9.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                card.latestChapter?.let { latest ->
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC0D0D14))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .align(Alignment.BottomEnd),
                    ) {
                        Text(
                            text = "Ch $latest",
                            color = Color(0xFFC9C9DA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        card.originLabel?.let { label ->
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}
