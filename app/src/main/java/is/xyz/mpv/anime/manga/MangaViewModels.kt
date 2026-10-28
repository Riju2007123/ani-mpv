package `is`.xyz.mpv.anime.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `is`.xyz.mpv.anime.Library
import `is`.xyz.mpv.anime.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_PAGE_SIZE = 30

sealed interface MangaHomeUiState {
    data object Loading : MangaHomeUiState
    data class Error(val message: String) : MangaHomeUiState
    data class Content(val data: MangaHomeData) : MangaHomeUiState
}

class MangaHomeViewModel : ViewModel() {
    var state by mutableStateOf<MangaHomeUiState>(MangaHomeUiState.Loading)
        private set

    var query by mutableStateOf("")
        private set

    var filterState by mutableStateOf(MangaFilterState())
        private set

    var searchResults by mutableStateOf<List<MangaCard>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    var isPageLoading by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var currentPage by mutableStateOf(1)
        private set

    /** Highest page known to contain results. Grows as the user pages forward. */
    var totalPages by mutableStateOf(1)
        private set

    private var searchJob: Job? = null

    init {
        loadHome()
    }

    fun loadHome(forceRefresh: Boolean = false) {
        if (isRefreshing) return
        viewModelScope.launch {
            if (state !is MangaHomeUiState.Content || forceRefresh) {
                state = MangaHomeUiState.Loading
            }
            isRefreshing = forceRefresh
            state = try {
                MangaHomeUiState.Content(MangaRepository.getHome(forceRefresh))
            } catch (t: Throwable) {
                MangaHomeUiState.Error(t.userMessage())
            }
            isRefreshing = false
        }
    }

    fun refresh() {
        MangaRepository.clearHomeCache()
        loadHome(forceRefresh = true)
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        triggerSearch()
    }

    fun updateSort(sort: String) {
        filterState = filterState.copy(sort = sort)
        triggerSearch()
    }

    fun toggleTag(tagId: String) {
        val newTags = if (tagId in filterState.selectedTags) {
            filterState.selectedTags - tagId
        } else {
            filterState.selectedTags + tagId
        }
        filterState = filterState.copy(selectedTags = newTags)
        triggerSearch()
    }

    fun toggleDemographic(demographic: String) {
        val newDemo = if (demographic in filterState.demographics) {
            filterState.demographics - demographic
        } else {
            filterState.demographics + demographic
        }
        filterState = filterState.copy(demographics = newDemo)
        triggerSearch()
    }

    fun setStatus(status: String?) {
        filterState = filterState.copy(status = status)
        triggerSearch()
    }

    fun setContentRating(rating: String?) {
        filterState = filterState.copy(contentRating = rating)
        triggerSearch()
    }

    fun resetFilters() {
        filterState = MangaFilterState()
        triggerSearch()
    }

    fun goToPage(page: Int) {
        if (page == currentPage && searchResults.isNotEmpty()) return
        val targetPage = page.coerceAtLeast(1)
        currentPage = targetPage
        isPageLoading = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                val results = MangaRepository.searchWithFilters(query.trim(), filterState, targetPage)
                applyResults(results, targetPage)
            } catch (_: Exception) {
                searchResults = emptyList()
                totalPages = targetPage
            } finally {
                isPageLoading = false
            }
        }
    }

    private fun applyResults(results: List<MangaCard>, page: Int) {
        searchResults = results
        totalPages = when {
            results.isEmpty() -> page.coerceAtLeast(1)
            results.size < SEARCH_PAGE_SIZE -> page
            else -> maxOf(totalPages, page + 1)
        }
    }

    private fun triggerSearch() {
        searchJob?.cancel()
        currentPage = 1
        totalPages = 1

        val hasFilters = filterState.selectedTags.isNotEmpty() ||
                filterState.demographics.isNotEmpty() ||
                !filterState.status.isNullOrBlank() ||
                !filterState.contentRating.isNullOrBlank() ||
                filterState.sort != "latest"

        if (query.isBlank() && !hasFilters) {
            isSearching = false
            searchResults = emptyList()
            return
        }

        isSearching = true
        isPageLoading = true
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val results = MangaRepository.searchWithFilters(query.trim(), filterState, 1)
                applyResults(results, 1)
            } catch (_: Exception) {
                searchResults = emptyList()
                totalPages = 1
            } finally {
                isPageLoading = false
            }
        }
    }
}

sealed interface MangaDetailsUiState {
    data object Loading : MangaDetailsUiState
    data class Error(val message: String) : MangaDetailsUiState
    data class Content(val details: MangaDetails, val chapters: List<MangaChapter>) : MangaDetailsUiState
}

class MangaDetailsViewModel : ViewModel() {
    var state by mutableStateOf<MangaDetailsUiState>(MangaDetailsUiState.Loading)
        private set

    var loadedId: String? = null
        private set

    fun load(mangaId: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && mangaId == loadedId && state is MangaDetailsUiState.Content) return
        loadedId = mangaId

        viewModelScope.launch {
            if (state !is MangaDetailsUiState.Content || forceRefresh) {
                state = MangaDetailsUiState.Loading
            }
            state = try {
                val details = MangaRepository.getDetails(mangaId)
                val chapters = MangaRepository.getChapters(mangaId)
                MangaDetailsUiState.Content(details, chapters)
            } catch (t: Throwable) {
                MangaDetailsUiState.Error(t.userMessage())
            }
        }
    }

    fun retry() {
        loadedId?.let { load(it, forceRefresh = true) }
    }
}

sealed interface MangaReaderUiState {
    data object Loading : MangaReaderUiState
    data class Error(val message: String) : MangaReaderUiState
    data class Content(
        val chapter: MangaChapter,
        val pages: List<MangaPage>,
        val chapters: List<MangaChapter>,
        val index: Int,
        val mangaTitle: String,
        val coverUrl: String?,
        val mangaSlug: String?,
        val initialPage: Int,
    ) : MangaReaderUiState {
        val hasPrev: Boolean get() = index > 0
        val hasNext: Boolean get() = index >= 0 && index < chapters.size - 1
    }
}

class MangaReaderViewModel : ViewModel() {
    var state by mutableStateOf<MangaReaderUiState>(MangaReaderUiState.Loading)
        private set

    private var allChapters = emptyList<MangaChapter>()
    private var currentIndex = -1
    private var currentMangaId = ""
    private var mangaDetails: MangaDetails? = null
    private var loadJob: Job? = null

    fun loadChapter(mangaId: String, initialChapterId: String, startPage: Int = 1) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state = MangaReaderUiState.Loading
            try {
                if (mangaId.isNotBlank() && mangaId != currentMangaId) {
                    currentMangaId = mangaId
                    allChapters = MangaRepository.getChapters(mangaId)
                    mangaDetails = MangaRepository.peekDetails(mangaId)
                        ?: runCatching { MangaRepository.getDetails(mangaId) }.getOrNull()
                } else if (allChapters.isEmpty() && mangaId.isNotBlank()) {
                    allChapters = MangaRepository.getChapters(mangaId)
                    mangaDetails = MangaRepository.peekDetails(mangaId)
                        ?: runCatching { MangaRepository.getDetails(mangaId) }.getOrNull()
                }

                currentIndex = allChapters.indexOfFirst { it.id == initialChapterId }
                if (currentIndex == -1 && allChapters.isNotEmpty()) {
                    currentIndex = 0
                }

                if (mangaDetails == null && mangaId.isNotBlank()) {
                    mangaDetails = MangaRepository.peekDetails(mangaId)
                        ?: runCatching { MangaRepository.getDetails(mangaId) }.getOrNull()
                }

                val currentChap = allChapters.getOrNull(currentIndex)
                    ?: MangaChapter(initialChapterId, "?", null)

                // Alternate titles let the fallback source find series whose Comick title differs.
                val titles = mangaDetails
                    ?.let { listOf(it.title) + it.altTitles }
                    .orEmpty()

                val history = if (mangaId.isNotBlank()) Library.mangaHistoryFor(mangaId) else null
                val sameChapter = history != null && (
                    history.chapterId == currentChap.id ||
                        chapterNumbersMatch(history.chapterNumber, currentChap.chapterNumber)
                    )
                val resumePage = if (sameChapter) history!!.pageNumber else startPage

                val pages = MangaRepository.getPages(
                    chapterId = currentChap.id,
                    mangaId = mangaId,
                    chapterNumber = currentChap.chapterNumber,
                    titles = titles,
                )

                state = MangaReaderUiState.Content(
                    chapter = currentChap,
                    pages = pages,
                    chapters = allChapters,
                    index = currentIndex,
                    mangaTitle = mangaDetails?.title ?: "Manga",
                    coverUrl = mangaDetails?.coverUrl,
                    mangaSlug = mangaDetails?.slug,
                    initialPage = resumePage.coerceAtLeast(1),
                )

                // Warm the next chapter while the reader is showing this one.
                allChapters.getOrNull(currentIndex + 1)?.let { next ->
                    launch {
                        MangaRepository.prefetchPages(next.id, mangaId, next.chapterNumber, titles)
                    }
                }
            } catch (t: Throwable) {
                state = MangaReaderUiState.Error(t.userMessage())
            }
        }
    }

    fun openChapter(chapter: MangaChapter) {
        loadChapter(currentMangaId, chapter.id)
    }

    fun nextChapter() {
        val next = allChapters.getOrNull(currentIndex + 1) ?: return
        loadChapter(currentMangaId, next.id)
    }

    fun prevChapter() {
        val prev = allChapters.getOrNull(currentIndex - 1) ?: return
        loadChapter(currentMangaId, prev.id)
    }

    /** Re-fetches pages for the current chapter (used when a source upload has no images). */
    fun retry() {
        val content = state as? MangaReaderUiState.Content ?: return
        MangaRepository.invalidatePages(content.chapter.id)
        loadChapter(currentMangaId, content.chapter.id, content.initialPage)
    }
}

private fun chapterNumbersMatch(a: String, b: String): Boolean {
    val first = a.toDoubleOrNull()
    val second = b.toDoubleOrNull()
    return if (first != null && second != null) first == second else a == b
}
