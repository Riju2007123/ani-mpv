package `is`.xyz.mpv.anime

import android.util.Log

/**
 * Single entry point for everything the anime UI needs. Uses AniCliRunner
 * or HianimeApi based on the "anime_provider_source" setting in Settings,
 * with high-speed in-memory caching for instant screen loading.
 */
object AnimeRepository {
    private const val TAG = "AnimeRepository"
    private val detailsCache = HashMap<String, AnimeDetails>()
    private val episodeCache = HashMap<String, List<Episode>>()
    private val searchCache = HashMap<String, List<AnimeCard>>()
    private var homeCache: HomeData? = null
    private var homeCacheTime = 0L

    suspend fun home(): HomeData {
        val now = System.currentTimeMillis()
        if (homeCache != null && (now - homeCacheTime) < 5 * 60 * 1000) {
            return homeCache!!
        }
        val data = HianimeApi.home()
        homeCache = data
        homeCacheTime = now
        return data
    }

    suspend fun search(query: String): List<AnimeCard> {
        val trimmed = query.trim().lowercase()
        synchronized(searchCache) { searchCache[trimmed] }?.let { return it }

        val src = Library.getProviderSource()
        val results = if (src == "hianime") HianimeApi.search(query) else AniCliRunner.search(query)

        if (results.isNotEmpty()) {
            synchronized(searchCache) { searchCache[trimmed] = results }
        }
        return results
    }

    private val genreCache = HashMap<String, List<AnimeCard>>()

    suspend fun genre(genreSlug: String): List<AnimeCard> {
        synchronized(genreCache) { genreCache[genreSlug] }?.let { return it }
        val results = try {
            HianimeApi.fetchCategory(genreSlug)
        } catch (_: Exception) {
            emptyList()
        }
        if (results.isNotEmpty()) {
            synchronized(genreCache) { genreCache[genreSlug] = results }
        }
        return results
    }

    suspend fun details(slug: String): AnimeDetails {
        synchronized(detailsCache) { detailsCache[slug] }?.let { return it }

        val scraped = try {
            HianimeApi.details(slug)
        } catch (e: Exception) {
            Log.w(TAG, "Scraper details failed for $slug: $e")
            HianimeApi.Details(
                slug = slug,
                animeId = slug,
                title = slug.replace('-', ' ').capitalizeWords(),
                cover = null,
                description = null,
                genres = emptyList(),
                subCount = 0,
                dubCount = 0,
            )
        }

        val meta = AniListApi.lookup(scraped.title)

        val finalSubCount = if (scraped.subCount > 0) {
            scraped.subCount
        } else {
            meta?.episodes ?: 0
        }

        val details = AnimeDetails(
            slug = scraped.slug,
            title = scraped.title,
            animeId = scraped.animeId,
            cover = meta?.cover ?: scraped.cover,
            banner = meta?.banner ?: scraped.cover,
            description = meta?.description ?: scraped.description,
            genres = meta?.genres?.takeIf { it.isNotEmpty() } ?: scraped.genres,
            score = meta?.score,
            year = meta?.year,
            format = meta?.format,
            totalEpisodes = meta?.episodes ?: finalSubCount,
            subCount = finalSubCount,
            dubCount = if (scraped.dubCount > 0) scraped.dubCount else finalSubCount,
        )

        synchronized(detailsCache) { detailsCache[slug] = details }
        return details
    }

    suspend fun episodes(animeId: String): List<Episode> {
        synchronized(episodeCache) { episodeCache[animeId] }?.let { return it }
        val src = Library.getProviderSource()
        var episodes = if (src == "hianime") {
            try { HianimeApi.episodes(animeId) } catch (_: Exception) { emptyList() }
        } else {
            try { AniCliRunner.getEpisodes(animeId) } catch (_: Exception) { emptyList() }
        }

        // Fallback: If scraper returned 0 episodes, generate numbers 1..N using AniList/details count
        if (episodes.isEmpty()) {
            val details = synchronized(detailsCache) {
                detailsCache[animeId] ?: detailsCache.values.firstOrNull { it.animeId == animeId || it.slug == animeId }
            }
            val count = details?.subCount ?: details?.totalEpisodes ?: 0
            if (count > 0) {
                episodes = (1..count).map { num ->
                    Episode(
                        number = "$num",
                        id = "${animeId}_ep_$num",
                        title = "Episode $num",
                    )
                }
            }
        }

        synchronized(episodeCache) { episodeCache[animeId] = episodes }
        return episodes
    }

    suspend fun resolve(episodeId: String, mode: String): StreamInfo {
        val src = Library.getProviderSource()

        if (episodeId.contains("_ep_")) {
            // Derived fallback episode ID: extract animeId and episode number
            val parts = episodeId.split("_ep_")
            val rawAnimeId = parts[0]
            val epNumStr = parts.getOrNull(1) ?: "1"

            val details = synchronized(detailsCache) {
                detailsCache[rawAnimeId] ?: detailsCache.values.firstOrNull { it.animeId == rawAnimeId || it.slug == rawAnimeId }
            }
            val searchTitle = details?.title ?: rawAnimeId.replace('-', ' ')

            try {
                val stream = AniCliRunner.resolveStream("$searchTitle $epNumStr", mode)
                if (stream.url.startsWith("http")) {
                    return stream
                }
            } catch (e: Exception) {
                Log.w(TAG, "AniCliRunner resolve fallback failed: $e")
            }
        }

        if (src == "ani-cli") {
            try {
                val stream = AniCliRunner.resolveStream(episodeId, mode)
                if (stream.url.startsWith("http")) return stream
            } catch (e: Exception) {
                Log.w(TAG, "AniCliRunner resolve failed: $e")
            }
        }

        return try {
            HianimeApi.resolve(episodeId, mode)
        } catch (e: Exception) {
            Log.w(TAG, "HianimeApi resolve failed (403/Cloudflare), retrying via AniCliRunner: $e")
            AniCliRunner.resolveStream(episodeId, mode)
        }
    }

    private fun String.capitalizeWords(): String =
        split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}