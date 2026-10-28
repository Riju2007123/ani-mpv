package `is`.xyz.mpv.anime

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Scraper for the HiAnime pages that ani-cli also uses.
 *
 * The site returns plain HTML/JSON; a desktop user agent is enough to get past
 * the CDN in most regions. Stream links come from the ZokoAnime embed, which
 * ships its player config as base64(json XOR "otaku-embed-v1").
 */
object HianimeApi {
    private const val BASE = "https://hianime.at"
    private const val XOR_KEY = "otaku-embed-v1"

    data class Details(
        val slug: String,
        val animeId: String,
        val title: String,
        val cover: String?,
        val description: String?,
        val genres: List<String>,
        val subCount: Int,
        val dubCount: Int,
    )

    suspend fun home(): HomeData = withContext(Dispatchers.IO) {
        val doc = fetchDoc("$BASE/home")

        val spotlight = doc.select("div.deslide-item").mapNotNull { el ->
            val title = el.selectFirst("div.desi-head-title")?.text()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val href = el.selectFirst("a[href*=/watch/]")?.attr("href")
                ?: el.selectFirst("div.desi-buttons a[href]")?.attr("href")
                ?: return@mapNotNull null
            val slug = slugOf(href) ?: return@mapNotNull null
            val image = el.selectFirst("img")?.let { absImage(it) }
            AnimeCard(
                slug = slug,
                title = title,
                cover = image,
                banner = image,
                description = el.selectFirst("div.desi-description")?.text()?.trim(),
                subCount = countOf(el, ".tick-sub"),
                dubCount = countOf(el, ".tick-dub"),
            )
        }

        val trending = doc.select("#trending-home .item").mapNotNull { el ->
            val title = el.selectFirst(".film-title")?.text()?.trim().orEmpty()
            val slug = slugOf(el.selectFirst("a.film-poster")?.attr("href") ?: return@mapNotNull null)
                ?: return@mapNotNull null
            if (title.isEmpty()) return@mapNotNull null
            AnimeCard(
                slug = slug,
                title = title,
                cover = el.selectFirst("img")?.let { absImage(it) },
                rank = el.selectFirst(".number span")?.text()?.trim()?.toIntOrNull(),
            )
        }

        val rows = mutableListOf<HomeRow>()
        if (trending.isNotEmpty())
            rows += HomeRow("Trending now", trending)

        doc.select("section.block_area").forEach { section ->
            val heading = section.selectFirst("h2.cat-heading")?.text()?.trim() ?: return@forEach
            val items = section.select("div.flw-item").mapNotNull(::parseCard)
            if (items.isNotEmpty())
                rows += HomeRow(heading, items)
        }

        HomeData(spotlight, rows)
    }

    suspend fun search(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val doc = fetchDoc("$BASE/search?keyword=$encoded")
        val scope = doc.selectFirst("#main-content") ?: doc
        scope.select("div.flw-item").mapNotNull(::parseCard).distinctBy { it.slug }
    }

    suspend fun fetchCategory(category: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val url = when (category) {
            "subbed", "subbed-anime" -> "$BASE/subbed-anime"
            "dubbed", "dubbed-anime" -> "$BASE/dubbed-anime"
            "popular", "most-popular" -> "$BASE/most-popular"
            "top-airing" -> "$BASE/top-airing"
            "movie", "movies" -> "$BASE/movie"
            "tv" -> "$BASE/tv"
            "favorite", "most-favorite" -> "$BASE/most-favorite"
            "completed" -> "$BASE/completed"
            else -> {
                val encoded = URLEncoder.encode(category, StandardCharsets.UTF_8.name())
                "$BASE/search?keyword=$encoded"
            }
        }
        val doc = fetchDoc(url)
        val scope = doc.selectFirst("#main-content") ?: doc
        scope.select("div.flw-item").mapNotNull(::parseCard).distinctBy { it.slug }
    }

    suspend fun details(slug: String): Details = withContext(Dispatchers.IO) {
        val doc = fetchDoc("$BASE/$slug")
        val title = doc.selectFirst("div.anisc-detail h2.film-name")?.text()?.trim()
            ?: doc.selectFirst("h2.film-name")?.text()?.trim()
            ?: throw IOException("Could not read anime details")
        val animeId = Regex("(\\d+)$").find(slug)?.value
            ?: Regex("data-animeid=\"(\\d+)\"").find(doc.html())?.groupValues?.get(1)
            ?: throw IOException("Could not find anime id")
        Details(
            slug = slug,
            animeId = animeId,
            title = title,
            cover = doc.selectFirst("div.anisc-poster img.film-poster-img")
                ?.let { absImage(it) }
                ?: doc.selectFirst("img.film-poster-img")?.let { absImage(it) },
            description = doc.selectFirst("div.film-description div.text")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() },
            genres = doc.select("div.anisc-info a[href*=/genres/]").map { it.text().trim() }
                .filter { it.isNotEmpty() }.distinct(),
            subCount = countOf(doc, ".tick-item.tick-sub"),
            dubCount = countOf(doc, ".tick-item.tick-dub"),
        )
    }

    suspend fun episodes(animeId: String): List<Episode> = withContext(Dispatchers.IO) {
        val json = Http.get("$BASE/api/theme/episode/list/$animeId")
        val html = JSONObject(json).optString("html")
        val doc = Jsoup.parse(html)
        doc.select("a.ep-item[data-number]").map { el ->
            val number = el.attr("data-number")
            val name = el.selectFirst(".ep-name")?.text()?.trim()
            Episode(
                number = number,
                id = el.attr("data-id"),
                title = name?.takeIf { it.isNotEmpty() && !it.equals("Episode $number", true) },
            )
        }.filter { it.id.isNotEmpty() }
    }

    /**
     * Resolves one episode to a direct HLS url. Only the ZokoAnime server is
     * supported (that is also the only one ani-cli understands).
     */
    suspend fun resolve(episodeId: String, mode: String): StreamInfo = withContext(Dispatchers.IO) {
        val serversJson = Http.get("$BASE/api/theme/episode/servers?episodeId=$episodeId")
        val serversHtml = JSONObject(serversJson).optString("html")
        val servers = Jsoup.parse(serversHtml).select("div.server-item[data-type=$mode]")

        val server = servers.firstOrNull {
            it.attr("data-server-name").equals("ZokoAnime", ignoreCase = true)
        } ?: throw IOException("This episode only has unsupported servers")

        val hash = server.attr("data-hash")
        if (hash.isBlank()) throw IOException("Empty stream hash")

        val embedUrl = String(Base64.decode(hash, Base64.DEFAULT), StandardCharsets.UTF_8).trim()
        if (!embedUrl.startsWith("http")) throw IOException("Bad embed url")

        val referrer = originOf(embedUrl) ?: throw IOException("Bad embed url")
        val embedHtml = Http.get(embedUrl, referrer)
        val blob = Regex("window\\.__P=\"([^\"]+)\"").find(embedHtml)?.groupValues?.get(1)
            ?: throw IOException("Stream config not found")

        val config = JSONObject(deobfuscate(blob))
        val url = config.optString("src")
        if (!url.startsWith("http")) throw IOException("No playable stream")

        val subtitles = mutableListOf<Subtitle>()
        config.optJSONArray("subtitles")?.let { array ->
            for (i in 0 until array.length()) {
                val sub = array.optJSONObject(i) ?: continue
                val src = sub.optString("src")
                if (!src.startsWith("http")) continue
                subtitles += Subtitle(
                    label = sub.optString("label").ifBlank { sub.optString("lang", "Subtitle") },
                    url = src,
                    isDefault = sub.optBoolean("default", false),
                )
            }
        }

        StreamInfo(url = url, referrer = referrer, subtitles = subtitles)
    }

    // ---- parsing helpers ----

    private suspend fun fetchDoc(url: String): Document {
        val html = Http.get(url)
        val doc = Jsoup.parse(html, BASE)
        if (doc.title().contains("Just a moment", ignoreCase = true))
            throw IOException("HiAnime is showing a Cloudflare challenge")
        return doc
    }

    private fun parseCard(el: Element): AnimeCard? {
        val link = el.selectFirst("a.film-poster-ahref")
            ?: el.selectFirst("div.film-poster a[href]")
            ?: el.selectFirst("h3.film-name a[href]")
            ?: return null
        val slug = slugOf(link.attr("href")) ?: return null
        val title = el.selectFirst("h3.film-name a")?.text()?.trim()
            ?: link.attr("title").trim().takeIf { it.isNotEmpty() }
            ?: return null
        return AnimeCard(
            slug = slug,
            title = title,
            cover = el.selectFirst("img.film-poster-img")?.let { absImage(it) }
                ?: el.selectFirst("img")?.let { absImage(it) },
            subCount = countOf(el, ".tick-item.tick-sub"),
            dubCount = countOf(el, ".tick-item.tick-dub"),
            description = el.selectFirst("div.fd-infor")?.text()?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun absImage(el: Element): String? {
        val raw = el.attr("data-src").ifBlank { el.attr("src") }.trim()
        return when {
            raw.isEmpty() -> null
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            else -> el.absUrl("src").ifBlank { raw }
        }
    }

    private fun countOf(el: Element, selector: String): Int =
        el.selectFirst(selector)?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 0

    private fun slugOf(href: String): String? {
        if (href.isBlank()) return null
        val path = href.substringBefore('?').substringBefore('#').trimEnd('/')
        val candidate = when {
            path.contains("/watch/") -> path.substringAfter("/watch/")
            else -> path.substringAfterLast('/')
        }.substringBefore('?').substringBefore('&')
        return candidate.takeIf { it.isNotBlank() && it != "watch" }
    }

    private fun deobfuscate(blob: String): String {
        val bytes = Base64.decode(blob, Base64.DEFAULT)
        val key = XOR_KEY.toByteArray(StandardCharsets.UTF_8)
        val out = ByteArray(bytes.size)
        for (i in bytes.indices)
            out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        return String(out, StandardCharsets.UTF_8)
    }
}
