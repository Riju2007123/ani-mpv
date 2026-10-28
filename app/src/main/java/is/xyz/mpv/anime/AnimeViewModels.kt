package `is`.xyz.mpv.anime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.UnknownHostException

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val data: HomeData) : HomeUiState
}

class HomeViewModel : ViewModel() {
    var state by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        private set

    var selectedGenre by mutableStateOf("all")
        private set

    private val loadedGenrePages = mutableSetOf<String>()
    private var isFetchingMore = false

    private val genreQueue = listOf(
        "popular" to "Most Popular",
        "top-airing" to "Top Airing",
        "action" to "Action & Adventure",
        "fantasy" to "Sci-Fi & Fantasy",
        "romance" to "Romance & Slice of Life",
        "supernatural" to "Supernatural & Thriller",
        "comedy" to "Comedy & Humor",
        "movie" to "Anime Movies"
    )

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = HomeUiState.Loading
            selectedGenre = "all"
            loadedGenrePages.clear()
            state = try {
                HomeUiState.Content(AnimeRepository.home())
            } catch (t: Throwable) {
                HomeUiState.Error(t.userMessage())
            }
        }
    }

    fun selectGenre(genreSlug: String) {
        if (selectedGenre == genreSlug) return
        selectedGenre = genreSlug

        if (genreSlug == "all") {
            load()
            return
        }

        viewModelScope.launch {
            state = HomeUiState.Loading
            state = try {
                val items = AnimeRepository.genre(genreSlug)
                val rowTitle = genreQueue.firstOrNull { it.first == genreSlug }?.second ?: genreSlug.replaceFirstChar { it.uppercase() }
                val homeData = HomeData(
                    spotlight = items.take(5),
                    rows = listOf(HomeRow(rowTitle, items))
                )
                HomeUiState.Content(homeData)
            } catch (t: Throwable) {
                HomeUiState.Error(t.userMessage())
            }
        }
    }

    fun loadNextGenreRow() {
        val currentContent = state as? HomeUiState.Content ?: return
        if (isFetchingMore || selectedGenre != "all") return

        val nextGenre = genreQueue.firstOrNull { it.first !in loadedGenrePages } ?: return
        isFetchingMore = true

        viewModelScope.launch {
            try {
                val items = AnimeRepository.genre(nextGenre.first)
                if (items.isNotEmpty()) {
                    loadedGenrePages.add(nextGenre.first)
                    val updatedRows = currentContent.data.rows + HomeRow(nextGenre.second, items)
                    state = HomeUiState.Content(currentContent.data.copy(rows = updatedRows))
                }
            } catch (_: Exception) {
            } finally {
                isFetchingMore = false
            }
        }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
    data class Results(val items: List<AnimeCard>) : SearchUiState
}

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set

    var state by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set

    private var job: Job? = null

    fun onQueryChange(newQuery: String) {
        query = newQuery
        job?.cancel()

        if (newQuery.isBlank()) {
            state = SearchUiState.Idle
            return
        }

        job = viewModelScope.launch {
            delay(350)
            state = SearchUiState.Loading
            state = try {
                val results = AnimeRepository.search(newQuery.trim())
                if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Results(results)
            } catch (t: Throwable) {
                SearchUiState.Error(t.userMessage())
            }
        }
    }

    fun retry() {
        val current = query
        if (current.isNotBlank()) onQueryChange(current)
    }
}

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Error(val message: String) : DetailsUiState
    data class Content(val details: AnimeDetails, val episodes: List<Episode>) : DetailsUiState
}

class DetailsViewModel : ViewModel() {
    var state by mutableStateOf<DetailsUiState>(DetailsUiState.Loading)
        private set

    var mode by mutableStateOf("sub")
        private set

    private var loadedSlug: String? = null

    fun load(slug: String) {
        if (slug == loadedSlug && state is DetailsUiState.Content) return
        loadedSlug = slug

        viewModelScope.launch {
            state = DetailsUiState.Loading
            state = try {
                val details = AnimeRepository.details(slug)
                val episodes = AnimeRepository.episodes(details.animeId)
                DetailsUiState.Content(details, episodes)
            } catch (t: Throwable) {
                DetailsUiState.Error(t.userMessage())
            }
        }
    }

    fun changeMode(newMode: String) {
        mode = newMode
    }

    suspend fun resolve(episodeId: String): StreamInfo = AnimeRepository.resolve(episodeId, mode)
}

fun Throwable.userMessage(): String {
    val text = message.orEmpty()
    return when {
        this is UnknownHostException || this is ConnectException -> "No internet connection."
        text.contains("Cloudflare", ignoreCase = true) ->
            "HiAnime is blocking this network right now (Cloudflare). Try again later."
        text.startsWith("HTTP 403") -> "HiAnime refused the request (HTTP 403)."
        text.contains("unsupported servers", ignoreCase = true) ->
            "This episode is only on servers the app can't use yet."
        text.isNotBlank() -> text
        else -> "Something went wrong. Try again."
    }
}
