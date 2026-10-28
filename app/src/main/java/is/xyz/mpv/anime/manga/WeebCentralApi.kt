package `is`.xyz.mpv.anime.manga

import android.util.Log
import `is`.xyz.mpv.anime.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * WeebCentral fallback source.
 *
 * Comick only stores page images for chapters it uploaded itself. Chapters hosted by external
 * scanlation groups come back with `md_images: []` and `mdid: null`, which is why chapter 2+
 * often looked empty. WeebCentral usually carries the same series, so we resolve the pages
 * there by title and chapter number.
 */
object WeebCentralApi {
    private const val BASE_URL = "https://weebcentral.com"
    private const val MAX_TITLE_QUERIES = 4
    private const val MAX_SEARCH_CACHE = 96
    private const val MAX_CHAPTER_CACHE = 24

    private val CHAPTER_NUMBER = Regex(
        "(?:chapter|episode|ch|ep)\\.?\\s*([0-9]+(?:\\.[0-9]+)?)",
        RegexOption.IGNORE_CASE,
    )
    private val SERIES_ID = Regex("/series/([A-Za-z0-9]+)")
    private val PAGE_IMAGE = Regex("\\.(?:png|jpe?g|webp|gif|avif)(?:\\?|$)", RegexOption.IGNORE_CASE)

    private data class Series(val id: String, val title: String)
    private data class WcChapter(val id: String, val number: Double, val label: String)

    /** normalized title -> search results (empty list when nothing matched). */
    private val searchCache = HashMap<String, List<Series>>()
    /** series id -> chapters, newest first. */
    private val chapterCache = HashMap<String, List<WcChapter>>()

    /**
     * Resolves page images for [chapterNumber] of the series that best matches one of
     * [titles] (the Comick title plus its alternate titles). Returns an empty list when the
     * series or chapter cannot be found.
     */
    suspend fun pagesForTitle(titles: List<String>, chapterNumber: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            val number = chapterNumber.toDoubleOrNull() ?: return@withContext emptyList()
            val series = findSeries(titles) ?: return@withContext emptyList()

            val chapter = matchChapter(chaptersFor(series.id), number)
            if (chapter == null) {
                Log.i("WeebCentral", "No chapter $chapterNumber for '${series.title}'")
                return@withContext emptyList()
            }

            val pages = pagesForChapter(chapter.id)
            if (pages.isNotEmpty()) {
                Log.i(
                    "WeebCentral",
                    "Resolved ${pages.size} pages for '${series.title}' chapter $chapterNumber",
                )
            }
            pages
        }

    private suspend fun findSeries(titles: List<String>): Series? {
        val queries = titles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { TitleMatch.normalize(it) }
            .take(MAX_TITLE_QUERIES)

        var best: Series? = null
        var bestScore = 0.0

        for (query in queries) {
            val candidates = runCatching { searchSeries(query) }.getOrElse {
                Log.w("WeebCentral", "Search for '$query' failed: $it")
                emptyList()
            }
            for (candidate in candidates) {
                val score = TitleMatch.score(query, candidate.title)
                if (score > bestScore) {
                    best = candidate
                    bestScore = score
                }
            }
            if (bestScore >= 0.99) break
        }

        return best?.takeIf { bestScore >= TitleMatch.MIN_SCORE }
    }

    private suspend fun searchSeries(query: String): List<Series> {
        val key = TitleMatch.normalize(query)
        synchronized(searchCache) { searchCache[key] }?.let { return it }

        val url = buildString {
            append(BASE_URL)
            append("/search/data?limit=8&offset=0&text=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&sort=Best%20Match&order=Descending&official=Any&display_mode=Minimal%20Display")
        }
        val doc = Jsoup.parse(Http.get(url))

        val results = doc.select("article a[href]")
            .asSequence()
            .filter { it.attr("href").contains("/series/") }
            .mapNotNull { link ->
                val id = SERIES_ID.find(link.attr("href"))?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val title = link.selectFirst("h2")?.text()?.trim()
                    ?.ifBlank { null }
                    ?: link.attr("data-tip").trim()
                if (id.isBlank() || title.isBlank()) null else Series(id, title)
            }
            .distinctBy { it.id }
            .toList()

        if (results.isNotEmpty()) {
            synchronized(searchCache) {
                if (searchCache.size >= MAX_SEARCH_CACHE) searchCache.clear()
                searchCache[key] = results
            }
        }
        return results
    }

    private suspend fun chaptersFor(seriesId: String): List<WcChapter> {
        synchronized(chapterCache) { chapterCache[seriesId] }?.let { return it }

        val doc = Jsoup.parse(Http.get("$BASE_URL/series/$seriesId/full-chapter-list"))
        val chapters = doc.select("a[href]")
            .asSequence()
            .filter { it.attr("href").startsWith("/chapters/") }
            .mapNotNull { link ->
                val id = link.attr("href")
                    .removePrefix("/chapters/")
                    .substringBefore('?')
                    .substringBefore('/')
                    .trim()
                if (id.isBlank()) return@mapNotNull null

                val label = link.selectFirst("span.grow span")?.text()?.trim()
                    ?.ifBlank { null }
                    ?: link.text().trim()
                val number = CHAPTER_NUMBER.find(label)?.groupValues?.get(1)?.toDoubleOrNull()
                    ?: CHAPTER_NUMBER.find(link.text())?.groupValues?.get(1)?.toDoubleOrNull()
                    ?: return@mapNotNull null

                WcChapter(id, number, label)
            }
            .distinctBy { it.id }
            .toList()

        if (chapters.isNotEmpty()) {
            synchronized(chapterCache) {
                if (chapterCache.size >= MAX_CHAPTER_CACHE) chapterCache.clear()
                chapterCache[seriesId] = chapters
            }
        }
        return chapters
    }

    private fun matchChapter(chapters: List<WcChapter>, number: Double): WcChapter? {
        chapters.firstOrNull { it.number == number }?.let { return it }
        // Tolerate tiny rounding differences such as "2" vs "2.0".
        return chapters.firstOrNull { kotlin.math.abs(it.number - number) < 0.0001 }
    }

    private suspend fun pagesForChapter(chapterId: String): List<MangaPage> {
        val url = "$BASE_URL/chapters/$chapterId/images" +
            "?is_prev=False&current_page=1&reading_style=long_strip"
        val doc = Jsoup.parse(Http.get(url))
        val container = doc.selectFirst("#chapter-images") ?: doc

        val images = container.select("img")
            .mapNotNull { img ->
                val src = img.attr("src").ifBlank { img.attr("data-src") }.trim()
                if (src.isBlank() || src.startsWith("data:")) return@mapNotNull null
                if (!PAGE_IMAGE.containsMatchIn(src) && !src.contains("/manga/")) return@mapNotNull null
                when {
                    src.startsWith("http://") || src.startsWith("https://") -> src
                    else -> "$BASE_URL/${src.trimStart('/')}"
                }
            }
            .distinct()

        return images.mapIndexed { index, src -> MangaPage(pageNumber = index + 1, url = src) }
    }

    fun clearCaches() {
        synchronized(searchCache) { searchCache.clear() }
        synchronized(chapterCache) { chapterCache.clear() }
    }
}
