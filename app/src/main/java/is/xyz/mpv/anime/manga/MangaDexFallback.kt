package `is`.xyz.mpv.anime.manga

import android.util.Log
import `is`.xyz.mpv.anime.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Last-resort page source. MangaDex has lost a lot of licensed catalogue, but it still carries
 * many series, so it is tried after WeebCentral when a chapter has no readable Comick upload.
 */
object MangaDexFallback {
    private const val API_URL = "https://api.mangadex.org"
    private const val MAX_TITLE_QUERIES = 3
    private const val MAX_CACHE = 48

    private data class MdManga(val id: String, val titles: List<String>)

    /** normalized query -> search results. */
    private val searchCache = HashMap<String, List<MdManga>>()

    suspend fun pagesForTitle(titles: List<String>, chapterNumber: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            val number = chapterNumber.toDoubleOrNull() ?: return@withContext emptyList()
            val mangaId = findManga(titles) ?: return@withContext emptyList()
            val chapterId = findChapter(mangaId, chapterNumber, number)
                ?: return@withContext emptyList()

            val pages = atHomePages(chapterId)
            if (pages.isNotEmpty()) {
                Log.i("MangaDex", "Resolved ${pages.size} pages for chapter $chapterNumber")
            }
            pages
        }

    private suspend fun findManga(titles: List<String>): String? {
        val queries = titles
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { TitleMatch.normalize(it) }
            .take(MAX_TITLE_QUERIES)

        var bestId: String? = null
        var bestScore = 0.0

        for (query in queries) {
            val candidates = runCatching { searchManga(query) }.getOrElse {
                Log.w("MangaDex", "Search for '$query' failed: $it")
                emptyList()
            }
            for (candidate in candidates) {
                val score = candidate.titles.maxOfOrNull { TitleMatch.score(query, it) } ?: 0.0
                if (score > bestScore) {
                    bestId = candidate.id
                    bestScore = score
                }
            }
            if (bestScore >= 0.99) break
        }

        return bestId?.takeIf { bestScore >= TitleMatch.MIN_SCORE }
    }

    private suspend fun searchManga(query: String): List<MdManga> {
        val key = TitleMatch.normalize(query)
        synchronized(searchCache) { searchCache[key] }?.let { return it }

        val url = "$API_URL/manga?title=${encode(query)}&limit=20"
        val json = JSONObject(Http.get(url))
        val data = json.optJSONArray("data") ?: return emptyList()

        val results = mutableListOf<MdManga>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            if (id.isBlank()) continue
            val attributes = item.optJSONObject("attributes") ?: continue

            val titles = mutableListOf<String>()
            attributes.optJSONObject("title")?.let { title ->
                for (lang in title.keys()) {
                    title.optString(lang).takeIf { it.isNotBlank() }?.let { titles += it }
                }
            }
            attributes.optJSONArray("altTitles")?.let { altTitles ->
                for (j in 0 until altTitles.length()) {
                    val alt = altTitles.optJSONObject(j) ?: continue
                    for (lang in alt.keys()) {
                        alt.optString(lang).takeIf { it.isNotBlank() }?.let { titles += it }
                    }
                }
            }

            if (titles.isNotEmpty()) {
                results += MdManga(id, titles.distinct())
            }
        }

        if (results.isNotEmpty()) {
            synchronized(searchCache) {
                if (searchCache.size >= MAX_CACHE) searchCache.clear()
                searchCache[key] = results
            }
        }
        return results
    }

    private suspend fun findChapter(mangaId: String, rawNumber: String, number: Double): String? {
        val attempts = buildList {
            add(rawNumber)
            if (number % 1.0 == 0.0) {
                val integer = number.toInt().toString()
                if (integer != rawNumber) add(integer)
            }
        }

        for (value in attempts) {
            val url = "$API_URL/chapter?manga=$mangaId&chapter=${encode(value)}" +
                "&translatedLanguage[]=en&limit=10&includeEmptyPages=0"
            val json = runCatching { JSONObject(Http.get(url)) }.getOrElse {
                Log.w("MangaDex", "Chapter lookup $value failed: $it")
                continue
            }
            val data = json.optJSONArray("data") ?: continue

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val attributes = item.optJSONObject("attributes") ?: continue
                val pages = attributes.optInt("pages", 0)
                val external = attributes.optString("externalUrl", "").ifBlank { null }
                if (pages > 0 && external == null) {
                    val chapterId = item.optString("id", "")
                    if (chapterId.isNotBlank()) return chapterId
                }
            }
        }
        return null
    }

    private suspend fun atHomePages(chapterId: String): List<MangaPage> {
        val json = runCatching {
            JSONObject(Http.get("$API_URL/at-home/server/$chapterId"))
        }.getOrElse {
            Log.w("MangaDex", "At-home lookup failed: $it")
            return emptyList()
        }

        val baseUrl = json.optString("baseUrl", "").ifBlank { return emptyList() }
        val chapter = json.optJSONObject("chapter") ?: return emptyList()
        val hash = chapter.optString("hash", "").ifBlank { return emptyList() }
        val data = chapter.optJSONArray("data") ?: return emptyList()

        val pages = mutableListOf<MangaPage>()
        for (i in 0 until data.length()) {
            val file = data.optString(i, "")
            if (file.isBlank()) continue
            pages += MangaPage(pageNumber = pages.size + 1, url = "$baseUrl/data/$hash/$file")
        }
        return pages
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun clearCaches() {
        synchronized(searchCache) { searchCache.clear() }
    }
}
