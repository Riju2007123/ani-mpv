package `is`.xyz.mpv.anime

/**
 * Minimal anime card used by the browse rows and search results.
 * [slug] is the HiAnime page id, e.g. "bleach-1369".
 */
data class AnimeCard(
    val slug: String,
    val title: String,
    val cover: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val subCount: Int = 0,
    val dubCount: Int = 0,
    val rank: Int? = null,
)

/** Extra metadata pulled from AniList (covers, banner, synopsis, score, ...). */
data class AnimeMeta(
    val description: String? = null,
    val banner: String? = null,
    val cover: String? = null,
    val genres: List<String> = emptyList(),
    val score: Int? = null,
    val year: Int? = null,
    val format: String? = null,
    val episodes: Int? = null,
)

/** Full details for one anime, combining HiAnime and AniList data. */
data class AnimeDetails(
    val slug: String,
    val title: String,
    val animeId: String,
    val cover: String?,
    val banner: String?,
    val description: String?,
    val genres: List<String>,
    val score: Int?,
    val year: Int?,
    val format: String?,
    val totalEpisodes: Int?,
    val subCount: Int,
    val dubCount: Int,
)

data class Episode(
    val number: String,
    val id: String,
    val title: String?,
)

data class HomeRow(
    val title: String,
    val items: List<AnimeCard>,
)

data class HomeData(
    val spotlight: List<AnimeCard>,
    val rows: List<HomeRow>,
)

data class Subtitle(
    val label: String,
    val url: String,
    val isDefault: Boolean,
)

data class StreamInfo(
    val url: String,
    val referrer: String?,
    val subtitles: List<Subtitle>,
)
