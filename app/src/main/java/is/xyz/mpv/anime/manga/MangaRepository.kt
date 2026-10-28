package `is`.xyz.mpv.anime.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MangaRepository {
    private val detailsCache = HashMap<String, MangaDetails>()
    private val chapterCache = HashMap<String, List<MangaChapter>>()
    private val searchCache = HashMap<String, List<MangaCard>>()
    private val pageCache = object : LinkedHashMap<String, List<MangaPage>>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MangaPage>>?): Boolean =
            size > 24
    }
    private var homeCache: MangaHomeData? = null
    private var homeCacheTime = 0L

    suspend fun getHome(forceRefresh: Boolean = false): MangaHomeData {
        val now = System.currentTimeMillis()
        val cached = homeCache
        if (!forceRefresh && cached != null && (now - homeCacheTime) < 5 * 60 * 1000) {
            return cached
        }
        val data = MangaApi.getHome()
        homeCache = data
        homeCacheTime = now
        return data
    }

    fun clearHomeCache() {
        homeCache = null
        homeCacheTime = 0L
    }

    suspend fun search(query: String, tagId: String? = null): List<MangaCard> {
        val key = "$query|$tagId"
        synchronized(searchCache) { searchCache[key] }?.let { return it }

        val results = MangaApi.search(query, tagId)
        if (results.isNotEmpty()) {
            synchronized(searchCache) { searchCache[key] = results }
        }
        return results
    }

    suspend fun searchWithFilters(query: String, filterState: MangaFilterState, page: Int = 1): List<MangaCard> {
        val key = "$query|${filterState.sort}|${filterState.selectedTags}|${filterState.demographics}|${filterState.status}|${filterState.contentRating}|$page"
        synchronized(searchCache) { searchCache[key] }?.let { return it }

        val results = MangaApi.searchWithFilters(query, filterState, page)
        if (results.isNotEmpty()) {
            synchronized(searchCache) { searchCache[key] = results }
        }
        return results
    }

    suspend fun getDetails(mangaId: String): MangaDetails {
        synchronized(detailsCache) { detailsCache[mangaId] }?.let { return it }

        val details = MangaApi.getDetails(mangaId)
        synchronized(detailsCache) { detailsCache[mangaId] = details }
        return details
    }

    fun peekDetails(mangaId: String): MangaDetails? = synchronized(detailsCache) { detailsCache[mangaId] }

    suspend fun getChapters(mangaId: String): List<MangaChapter> {
        synchronized(chapterCache) { chapterCache[mangaId] }?.let { return it }

        val chapters = MangaApi.getChapters(mangaId)
        // Only keep fully downloaded chapter lists; a partial list hides later chapters.
        if (MangaApi.peekChapters(mangaId) != null) {
            synchronized(chapterCache) { chapterCache[mangaId] = chapters }
        }
        return chapters
    }

    fun peekChapters(mangaId: String): List<MangaChapter>? = synchronized(chapterCache) { chapterCache[mangaId] }

    suspend fun getPages(
        chapterId: String,
        mangaId: String? = null,
        chapterNumber: String? = null,
        titles: List<String> = emptyList(),
        forceRefresh: Boolean = false,
    ): List<MangaPage> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            synchronized(pageCache) { pageCache[chapterId] }?.let { return@withContext it }
        }

        val pages = MangaApi.getChapterPages(chapterId, mangaId, chapterNumber, titles)
        if (pages.isNotEmpty()) {
            synchronized(pageCache) { pageCache[chapterId] = pages }
        }
        pages
    }

    fun invalidatePages(chapterId: String) {
        synchronized(pageCache) { pageCache.remove(chapterId) }
    }

    /** Warms the page cache for the next chapter so switching feels instant. */
    suspend fun prefetchPages(
        chapterId: String,
        mangaId: String,
        chapterNumber: String,
        titles: List<String> = emptyList(),
    ) {
        runCatching { getPages(chapterId, mangaId, chapterNumber, titles) }
    }

    fun clearAll() {
        synchronized(detailsCache) { detailsCache.clear() }
        synchronized(chapterCache) { chapterCache.clear() }
        synchronized(searchCache) { searchCache.clear() }
        synchronized(pageCache) { pageCache.clear() }
        MangaApi.clearCaches()
        WeebCentralApi.clearCaches()
        MangaDexFallback.clearCaches()
        clearHomeCache()
    }
}
