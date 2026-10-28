package `is`.xyz.mpv.anime.manga

/**
 * Title comparison helpers used by the fallback manga sources.
 *
 * Comick titles rarely match another site's title character for character, so both sides are
 * reduced to lowercase alphanumerics before comparing. Parenthesised qualifiers such as
 * "(Official Colored)" are dropped because they describe the same series.
 */
internal object TitleMatch {
    private val BRACKETS = Regex("\\([^)]*\\)|\\[[^]]*]")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")

    fun normalize(raw: String): String = raw
        .lowercase()
        .replace(BRACKETS, " ")
        .replace("’", "'")
        .replace("'", "")
        .replace(NON_ALNUM, " ")
        .replace(SPACES, " ")
        .trim()

    /**
     * Scores [candidate] against [query]. 1.0 means an exact (normalized) match, values below
     * [MIN_SCORE] should be treated as a different series. The prefix rules deliberately
     * penalise extra words so "One Piece" does not match "One Piece Party".
     */
    fun score(query: String, candidate: String): Double {
        val a = normalize(query)
        val b = normalize(candidate)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (b.startsWith("$a ")) {
            val extra = b.split(' ').size - a.split(' ').size
            return 0.9 - 0.2 * extra
        }
        if (a.startsWith("$b ")) {
            val extra = a.split(' ').size - b.split(' ').size
            return 0.9 - 0.2 * extra
        }
        return 0.0
    }

    const val MIN_SCORE = 0.75
}
