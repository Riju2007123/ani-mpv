package `is`.xyz.mpv.anime.manga

data class MangaFilterState(
    val sort: String = "latest",
    val selectedTags: Set<String> = emptySet(),
    val demographics: Set<String> = emptySet(),
    val status: String? = null,
    val contentRating: String? = null,
)

data class MangaCard(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val description: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val latestChapter: String? = null,
    val rating: Double? = null,
    val country: String? = null,
    val mediaType: String? = null,
) {
    /** Short human label for the country of origin (Manga / Manhwa / Manhua). */
    val originLabel: String?
        get() = when (country?.lowercase()) {
            "jp" -> "Manga"
            "kr" -> "Manhwa"
            "cn", "tw", "hk" -> "Manhua"
            else -> null
        }
}

data class MangaDetails(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val status: String?,
    val genres: List<String>,
    val author: String?,
    val year: Int?,
    val rating: Double? = null,
    val chapterCount: Int? = null,
    val country: String? = null,
    val mediaType: String? = null,
    val slug: String? = null,
    /** Alternate titles (English first) used to match fallback page sources. */
    val altTitles: List<String> = emptyList(),
) {
    val originLabel: String?
        get() = when (country?.lowercase()) {
            "jp" -> "Manga"
            "kr" -> "Manhwa"
            "cn", "tw", "hk" -> "Manhua"
            else -> null
        }
}

data class MangaChapter(
    val id: String,
    val chapterNumber: String,
    val title: String?,
    val language: String = "en",
    val publishDate: String? = null,
    val group: String? = null,
    val volume: String? = null,
    val external: Boolean = false,
) {
    val numberValue: Double
        get() = chapterNumber.toDoubleOrNull() ?: 0.0

    val displayName: String
        get() = "Chapter $chapterNumber" +
            if (!title.isNullOrBlank()) " · $title" else ""
}

data class MangaPage(
    val pageNumber: Int,
    val url: String,
)

data class MangaHomeData(
    val trending: List<MangaCard>,
    val popular: List<MangaCard>,
    val latest: List<MangaCard>,
)
