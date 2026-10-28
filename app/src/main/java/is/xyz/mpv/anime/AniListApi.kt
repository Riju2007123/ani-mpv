package `is`.xyz.mpv.anime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.parser.Parser
import java.util.concurrent.ConcurrentHashMap

/**
 * AniList is only used to enrich HiAnime entries with nicer artwork,
 * descriptions, genres and scores. Results are cached in memory for instant responses.
 */
object AniListApi {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private val cache = ConcurrentHashMap<String, AnimeMeta>()

    private const val QUERY = """
        query (${'$'}search: String) {
          Media(search: ${'$'}search, type: ANIME) {
            id
            description(asHtml: false)
            bannerImage
            coverImage { extraLarge color }
            genres
            averageScore
            episodes
            seasonYear
            format
          }
        }
    """

    suspend fun lookup(title: String): AnimeMeta? = withContext(Dispatchers.IO) {
        val cleaned = cleanTitle(title)
        if (cleaned.isBlank()) return@withContext null
        cache[cleaned]?.let { return@withContext it }

        try {
            val payload = JSONObject()
                .put("query", QUERY)
                .put("variables", JSONObject().put("search", cleaned))
                .toString()
            val body = Http.postJson(ENDPOINT, payload)
            val media = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONObject("Media")
                ?: return@withContext null

            val cover = media.optJSONObject("coverImage")
                ?.optString("extraLarge")
                ?.takeIf { it.isNotBlank() }

            val meta = AnimeMeta(
                description = media.optString("description")
                    .takeIf { it.isNotBlank() }
                    ?.let(::stripHtml),
                banner = media.optString("bannerImage").takeIf { it.isNotBlank() },
                cover = cover,
                genres = media.optJSONArray("genres")?.let { array ->
                    (0 until array.length())
                        .mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
                } ?: emptyList(),
                score = media.optInt("averageScore").takeIf { it > 0 },
                year = media.optInt("seasonYear").takeIf { it > 0 },
                format = media.optString("format").takeIf { it.isNotBlank() },
                episodes = media.optInt("episodes").takeIf { it > 0 },
            )

            cache[cleaned] = meta
            meta
        } catch (_: Exception) {
            null
        }
    }

    private fun stripHtml(html: String): String =
        Parser.unescapeEntities(
            html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), ""),
            false,
        ).trim()
}