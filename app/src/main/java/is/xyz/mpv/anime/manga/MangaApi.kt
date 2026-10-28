package `is`.xyz.mpv.anime.manga

import android.util.Log
import `is`.xyz.mpv.anime.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object MangaApi {
    private const val BASE_URL = "https://api.comick.dev"
    private const val NOTASPIDER_URL = "https://comick-source-api.notaspider.dev/api/search"
    private const val IMAGE_BASE_URL = "https://meo.comick.pictures"
    private const val PAGE_SIZE = 30
    private const val CHAPTER_PAGE_SIZE = 300
    private const val MAX_CHAPTER_PAGES = 12

    /** Chapter lists are re-read by the page fallback, so keep the last few around. */
    private val chapterListCache = HashMap<String, List<MangaChapter>>()

    suspend fun getHome(): MangaHomeData = withContext(Dispatchers.IO) {
        val trendingUrl = "$BASE_URL/v1.0/search?sort=user_follow_count&limit=18"
        val popularUrl = "$BASE_URL/v1.0/search?sort=rating&limit=18"
        val latestUrl = "$BASE_URL/v1.0/search?sort=uploaded&limit=18"

        coroutineScope {
            val trending = async { runCatching { parseMangaList(Http.get(trendingUrl)) }.getOrDefault(emptyList()) }
            val popular = async { runCatching { parseMangaList(Http.get(popularUrl)) }.getOrDefault(emptyList()) }
            val latest = async { runCatching { parseMangaList(Http.get(latestUrl)) }.getOrDefault(emptyList()) }

            MangaHomeData(trending.await(), popular.await(), latest.await())
        }
    }

    suspend fun search(query: String, tagSlug: String? = null): List<MangaCard> = withContext(Dispatchers.IO) {
        val filterState = MangaFilterState(
            selectedTags = if (tagSlug != null) setOf(tagSlug) else emptySet()
        )
        searchWithFilters(query, filterState)
    }

    suspend fun searchWithFilters(
        query: String,
        filterState: MangaFilterState,
        page: Int = 1,
    ): List<MangaCard> = withContext(Dispatchers.IO) {
        val comickResults = try {
            searchComickDev(query, filterState, page)
        } catch (e: Exception) {
            Log.w("MangaApi", "ComickDev search error: $e")
            emptyList()
        }

        if (comickResults.isNotEmpty()) {
            return@withContext comickResults
        }

        val searchQuery = if (query.isNotBlank()) query else filterState.selectedTags.joinToString(" ")
        if (searchQuery.isNotBlank() && page == 1) {
            return@withContext searchWithNotaspiderApi(searchQuery)
        }

        emptyList()
    }

    private suspend fun searchWithNotaspiderApi(query: String): List<MangaCard> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MangaCard>()
        try {
            val payload = JSONObject().put("query", query).toString()
            val ndjson = Http.postJson(NOTASPIDER_URL, payload)

            ndjson.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && trimmed.startsWith("{")) {
                    try {
                        val obj = JSONObject(trimmed)
                        val resArray = obj.optJSONArray("results")
                        if (resArray != null) {
                            for (i in 0 until resArray.length()) {
                                val item = resArray.getJSONObject(i)
                                val title = item.optString("title", "").ifBlank { null }
                                val id = item.optString("id", "").ifBlank { null }
                                val coverImage = item.optString("coverImage", "").ifBlank { null }
                                val latestChapter = item.optString("latestChapter", "").ifBlank { null }
                                if (!title.isNullOrBlank() && !id.isNullOrBlank()) {
                                    list += MangaCard(
                                        id = id,
                                        title = title,
                                        coverUrl = coverImage,
                                        latestChapter = latestChapter
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w("MangaApi", "Notaspider API search error: $e")
        }
        list
    }

    private suspend fun searchComickDev(
        query: String,
        filterState: MangaFilterState,
        page: Int = 1,
    ): List<MangaCard> = withContext(Dispatchers.IO) {
        var url = "$BASE_URL/v1.0/search?page=$page&limit=$PAGE_SIZE"

        url += when (filterState.sort) {
            "popular" -> "&sort=user_follow_count"
            "rating" -> "&sort=rating"
            else -> "&sort=uploaded"
        }

        if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            url += "&q=$encoded"
        }

        if (filterState.selectedTags.isNotEmpty()) {
            val genresParam = filterState.selectedTags.joinToString(",")
            url += "&genres=$genresParam&genres_mode=and"
        }

        if (filterState.demographics.isNotEmpty()) {
            val demoMap = mapOf("shounen" to "1", "shoujo" to "2", "seinen" to "3", "josei" to "4")
            val demoNums = filterState.demographics.mapNotNull { demoMap[it] }.joinToString(",")
            if (demoNums.isNotEmpty()) {
                url += "&demographic=$demoNums"
            }
        }

        if (!filterState.status.isNullOrBlank()) {
            val statusNum = if (filterState.status == "ongoing") "1" else "2"
            url += "&status=$statusNum"
        }

        parseMangaList(Http.get(url))
    }

    suspend fun getDetails(hid: String): MangaDetails = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/comic/$hid"
        val json = JSONObject(Http.get(url))
        val comic = json.getJSONObject("comic")

        val title = comic.optString("title", "Manga").ifBlank { "Manga" }
        val description = comic.optString("desc", "").ifBlank { null }
        val statusNum = comic.optInt("status", 1)
        val status = if (statusNum == 1) "ongoing" else "completed"
        val year = comic.optInt("year", 0).takeIf { it > 0 }
        val slug = comic.optString("slug", "").ifBlank { null }
        val country = comic.optString("country", "").ifBlank { null }
        val mediaType = comic.optString("media_type", "").ifBlank { null }

        var coverFileName: String? = null
        comic.optJSONArray("md_covers")?.let { covers ->
            if (covers.length() > 0) {
                coverFileName = covers.getJSONObject(0).optString("b2key")
            }
        }

        val coverUrl = if (!coverFileName.isNullOrBlank()) "$IMAGE_BASE_URL/$coverFileName" else null

        // Authors live at the root of the response, not inside the comic object.
        var authorName: String? = null
        json.optJSONArray("authors")?.let { authors ->
            if (authors.length() > 0) {
                authorName = authors.optJSONObject(0)?.optString("name")?.ifBlank { null }
            }
        }
        if (authorName == null) {
            comic.optJSONArray("authors")?.let { authors ->
                if (authors.length() > 0) {
                    authorName = authors.optJSONObject(0)?.optString("name")?.ifBlank { null }
                }
            }
        }

        val genres = mutableListOf<String>()
        comic.optJSONArray("md_comic_md_genres")?.let { genresArray ->
            for (i in 0 until genresArray.length()) {
                val g = genresArray.getJSONObject(i).optJSONObject("md_genres")
                val name = g?.optString("name")
                if (!name.isNullOrBlank()) genres += name
            }
        }

        val rating = readDouble(comic, "bayesian_rating") ?: readDouble(comic, "rating")
        val chapterCount = comic.optInt("chapter_count", 0).takeIf { it > 0 }

        val altTitles = parseAltTitles(
            comic.optJSONArray("md_titles") ?: json.optJSONArray("md_titles"),
            title,
        )

        MangaDetails(
            id = hid,
            title = title,
            coverUrl = coverUrl,
            description = description,
            status = status,
            genres = genres,
            author = authorName,
            year = year,
            rating = rating,
            chapterCount = chapterCount,
            country = country,
            mediaType = mediaType,
            slug = slug,
            altTitles = altTitles,
        )
    }

    /**
     * Reads `md_titles` (English entries first) so the fallback sources can be searched with
     * the same names readers know the series by.
     */
    private fun parseAltTitles(array: JSONArray?, primaryTitle: String): List<String> {
        if (array == null) return emptyList()
        val english = mutableListOf<String>()
        val other = mutableListOf<String>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val value = item.optString("title", "").trim()
            if (value.isBlank() || value.equals(primaryTitle, ignoreCase = true)) continue
            if (item.optString("lang") == "en") {
                english += value
            } else {
                other += value
            }
        }

        return (english + other)
            .distinctBy { it.lowercase() }
            .take(10)
    }

    /**
     * Fetches every English chapter. The API caps a single page at 300 entries, so long
     * running series need several requests. Duplicate chapter numbers from different
     * scanlation groups are collapsed, preferring uploads that Comick hosts itself
     * (those always have readable pages).
     */
    suspend fun getChapters(hid: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        synchronized(chapterListCache) { chapterListCache[hid] }?.let { return@withContext it }

        val collected = mutableListOf<MangaChapter>()
        var total = -1
        var page = 1
        var complete = false

        while (page <= MAX_CHAPTER_PAGES) {
            val url = "$BASE_URL/comic/$hid/chapters?lang=en&limit=$CHAPTER_PAGE_SIZE&page=$page"
            val json = try {
                JSONObject(Http.get(url))
            } catch (e: Exception) {
                Log.w("MangaApi", "Chapter page $page failed: $e")
                break
            }

            val chaptersArray = json.optJSONArray("chapters")
            if (chaptersArray == null || chaptersArray.length() == 0) {
                complete = true
                break
            }
            if (total < 0) total = json.optInt("total", 0)

            for (i in 0 until chaptersArray.length()) {
                val item = chaptersArray.getJSONObject(i)
                val chapHid = item.optString("hid", "")
                if (chapHid.isBlank()) continue
                val chapNum = item.optString("chap", "").ifBlank { "${collected.size + 1}" }
                val rawTitle = item.optString("title", "")
                val title = if (rawTitle.isBlank() || rawTitle == "null") null else rawTitle
                val publishDate = item.optString("created_at", "").take(10).ifBlank { null }
                val group = item.optJSONArray("group_name")
                    ?.optString(0)
                    ?.ifBlank { null }
                val volume = item.optString("vol", "").ifBlank { null }
                val externalType = item.optString("external_type", "").ifBlank { null }
                val hasImages = (item.optJSONArray("md_images")?.length() ?: 0) > 0

                collected += MangaChapter(
                    id = chapHid,
                    chapterNumber = chapNum,
                    title = title,
                    language = "en",
                    publishDate = publishDate,
                    group = group,
                    volume = volume,
                    external = externalType != null && !hasImages,
                )
            }

            if (total in 1..collected.size) {
                complete = true
                break
            }
            if (chaptersArray.length() < CHAPTER_PAGE_SIZE) {
                complete = true
                break
            }
            page++
        }

        // Prefer readable (Comick-hosted) uploads when the same chapter exists multiple times.
        val byNumber = LinkedHashMap<String, MangaChapter>()
        for (chapter in collected) {
            val key = chapterKey(chapter.chapterNumber)
            val existing = byNumber[key]
            when {
                existing == null -> byNumber[key] = chapter
                existing.external && !chapter.external -> byNumber[key] = chapter
            }
        }

        val result = byNumber.values.sortedBy { it.numberValue }
        if (complete) {
            synchronized(chapterListCache) {
                if (chapterListCache.size >= 12) chapterListCache.clear()
                chapterListCache[hid] = result
            }
        }
        result
    }

    /** Returns the cached chapter list, or null when it has not been fetched completely yet. */
    fun peekChapters(hid: String): List<MangaChapter>? =
        synchronized(chapterListCache) { chapterListCache[hid] }

    fun clearCaches() {
        synchronized(chapterListCache) { chapterListCache.clear() }
    }

    private fun chapterKey(number: String): String {
        val value = number.toDoubleOrNull() ?: return number
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    /**
     * Resolves page images for a chapter.
     *
     * Comick only stores images for chapters it hosts itself, so external uploads come back
     * empty. The order is:
     *  1. the Comick chapter itself (direct images, MangaDex via [mdid], duplicate groups),
     *  2. WeebCentral by series title + chapter number,
     *  3. other Comick uploads of the same chapter number.
     */
    suspend fun getChapterPages(
        chapterHid: String,
        comicHid: String? = null,
        chapterNumber: String? = null,
        titles: List<String> = emptyList(),
    ): List<MangaPage> = withContext(Dispatchers.IO) {
        pagesForChapter(chapterHid)?.takeIf { it.isNotEmpty() }?.let { return@withContext it }

        if (chapterNumber != null && titles.isNotEmpty()) {
            val fallback = runCatching {
                WeebCentralApi.pagesForTitle(titles, chapterNumber)
            }.getOrElse {
                Log.w("MangaApi", "WeebCentral fallback failed: $it")
                emptyList()
            }
            if (fallback.isNotEmpty()) {
                Log.i("MangaApi", "Using WeebCentral pages for chapter $chapterNumber")
                return@withContext fallback
            }
        }

        // Try other uploads of the same chapter number (different scanlation groups).
        if (comicHid != null && chapterNumber != null) {
            val alternates = runCatching {
                getChapters(comicHid)
                    .filter { chapterKey(it.chapterNumber) == chapterKey(chapterNumber) && it.id != chapterHid }
            }.getOrDefault(emptyList())

            for (alt in alternates.take(6)) {
                val pages = pagesForChapter(alt.id)
                if (!pages.isNullOrEmpty()) {
                    Log.i("MangaApi", "Using alternate upload ${alt.id} (${alt.group}) for chapter $chapterNumber")
                    return@withContext pages
                }
            }
        }

        // Last resort: match the series on MangaDex by title.
        if (chapterNumber != null && titles.isNotEmpty()) {
            val mdPages = runCatching {
                MangaDexFallback.pagesForTitle(titles, chapterNumber)
            }.getOrElse {
                Log.w("MangaApi", "MangaDex fallback failed: $it")
                emptyList()
            }
            if (mdPages.isNotEmpty()) {
                Log.i("MangaApi", "Using MangaDex pages for chapter $chapterNumber")
                return@withContext mdPages
            }
        }

        emptyList()
    }

    /** Returns null when the chapter JSON could not be read at all. */
    private suspend fun pagesForChapter(chapterHid: String): List<MangaPage>? {
        val json = try {
            JSONObject(Http.get("$BASE_URL/chapter/$chapterHid"))
        } catch (e: Exception) {
            Log.w("MangaApi", "Chapter $chapterHid failed: $e")
            return null
        }

        val chapterObj = json.optJSONObject("chapter") ?: return null
        val pages = pagesFromImages(chapterObj.optJSONArray("md_images"))
        if (pages.isNotEmpty()) return pages

        // MangaDex-hosted chapter: resolve the page list through the at-home API.
        val mdid = chapterObj.optString("mdid", "")
        if (mdid.isNotBlank() && mdid != "null") {
            pagesFromMangaDex(mdid)?.let { if (it.isNotEmpty()) return it }
        }

        // Comick's duplicate-group list sometimes links to a readable upload.
        val dupGroups = json.optJSONArray("dupGroupChapters")
        if (dupGroups != null) {
            for (i in 0 until dupGroups.length()) {
                val altHid = dupGroups.optJSONObject(i)?.optString("hid", "").orEmpty()
                if (altHid.isBlank() || altHid == "null" || altHid == chapterHid) continue
                val altJson = runCatching { JSONObject(Http.get("$BASE_URL/chapter/$altHid")) }.getOrNull()
                    ?: continue
                val altChapter = altJson.optJSONObject("chapter") ?: continue
                val altPages = pagesFromImages(altChapter.optJSONArray("md_images"))
                if (altPages.isNotEmpty()) return altPages

                val altMdid = altChapter.optString("mdid", "")
                if (altMdid.isNotBlank() && altMdid != "null") {
                    val mdPages = pagesFromMangaDex(altMdid)
                    if (!mdPages.isNullOrEmpty()) return mdPages
                }
            }
        }

        return emptyList()
    }

    private fun pagesFromImages(images: JSONArray?): List<MangaPage> {
        if (images == null || images.length() == 0) return emptyList()
        val pages = mutableListOf<MangaPage>()
        for (i in 0 until images.length()) {
            val b2key = images.optJSONObject(i)?.optString("b2key", "").orEmpty()
            if (b2key.isBlank()) continue
            pages += MangaPage(pageNumber = pages.size + 1, url = "$IMAGE_BASE_URL/$b2key")
        }
        return pages
    }

    private suspend fun pagesFromMangaDex(mdid: String): List<MangaPage>? {
        return try {
            val mdJson = JSONObject(Http.get("https://api.mangadex.org/at-home/server/$mdid"))
            val baseUrl = mdJson.getString("baseUrl")
            val chapter = mdJson.getJSONObject("chapter")
            val hash = chapter.getString("hash")
            val data = chapter.getJSONArray("data")
            val pages = mutableListOf<MangaPage>()
            for (i in 0 until data.length()) {
                pages += MangaPage(
                    pageNumber = i + 1,
                    url = "$baseUrl/data/$hash/${data.getString(i)}",
                )
            }
            pages
        } catch (e: Exception) {
            Log.w("MangaApi", "MangaDex at-home error: $e")
            null
        }
    }

    /** Public Comick URL for a chapter, used by the "open in browser" fallback. */
    fun chapterWebUrl(slug: String?, chapterHid: String, chapterNumber: String): String {
        return if (!slug.isNullOrBlank()) {
            "https://comick.dev/comic/$slug/$chapterHid-chapter-$chapterNumber-en"
        } else {
            "https://comick.dev/"
        }
    }

    fun seriesWebUrl(slug: String?): String =
        if (!slug.isNullOrBlank()) "https://comick.dev/comic/$slug" else "https://comick.dev/"

    private fun parseMangaList(jsonStr: String): List<MangaCard> {
        val json = if (jsonStr.trim().startsWith("[")) {
            JSONArray(jsonStr)
        } else {
            JSONObject(jsonStr).optJSONArray("data") ?: JSONArray()
        }

        val list = mutableListOf<MangaCard>()
        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            val hid = item.optString("hid", "")
            if (hid.isBlank()) continue
            val title = item.optString("title", "Manga").ifBlank { "Manga" }
            val description = item.optString("desc", "").ifBlank { null }
            val statusNum = item.optInt("status", 1)
            val status = if (statusNum == 1) "ongoing" else "completed"

            var coverFileName: String? = null
            item.optJSONArray("md_covers")?.let { covers ->
                if (covers.length() > 0) {
                    coverFileName = covers.getJSONObject(0).optString("b2key")
                }
            }

            val coverUrl = if (!coverFileName.isNullOrBlank()) "$IMAGE_BASE_URL/$coverFileName" else null

            list += MangaCard(
                id = hid,
                title = title,
                coverUrl = coverUrl,
                description = description,
                status = status,
                latestChapter = item.optString("last_chapter", "").ifBlank { null },
                rating = readDouble(item, "bayesian_rating") ?: readDouble(item, "rating"),
                country = item.optString("country", "").ifBlank { null },
                mediaType = item.optString("media_type", "").ifBlank { null },
            )
        }
        return list
    }

    private fun readDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() && it > 0.0 }
    }
}
