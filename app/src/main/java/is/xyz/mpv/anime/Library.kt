package `is`.xyz.mpv.anime

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WatchItem(
    val slug: String,
    val title: String,
    val cover: String?,
    val addedAt: Long = System.currentTimeMillis(),
)

data class MangaWatchItem(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val addedAt: Long = System.currentTimeMillis(),
)

data class HistoryItem(
    val slug: String,
    val title: String,
    val cover: String?,
    val animeId: String,
    val episodeNumber: String,
    val episodeId: String,
    val mode: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isFinished: Boolean
        get() = durationMs > 0 && positionMs > durationMs * 0.94
}

data class MangaHistoryItem(
    val mangaId: String,
    val title: String,
    val coverUrl: String?,
    val chapterId: String,
    val chapterNumber: String,
    val pageNumber: Int = 1,
    val totalPages: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)

object DownloadStatus {
    const val QUEUED = "queued"
    const val DOWNLOADING = "downloading"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
    const val CANCELED = "canceled"
}

data class DownloadItem(
    val id: String,
    val slug: String,
    val title: String,
    val cover: String?,
    val animeId: String,
    val episodeNumber: String,
    val episodeId: String,
    val mode: String,
    val quality: Int,
    val status: String,
    val progress: Int = 0,
    val bytes: Long = 0L,
    val dir: String = "",
    val subtitles: List<LocalSubtitle> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val error: String? = null,
) {
    val isComplete: Boolean get() = status == DownloadStatus.COMPLETE
    val isActive: Boolean get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING
}

data class LocalSubtitle(val label: String, val path: String)

data class LibraryState(
    val watchlist: List<WatchItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val mangaWatchlist: List<MangaWatchItem> = emptyList(),
    val mangaHistory: List<MangaHistoryItem> = emptyList(),
)

/**
 * Local-first library: watchlist, continue-watching history, manga bookmarks and offline
 * downloads. Persisted as JSON in the app's private storage.
 */
object Library {
    private const val FILE_NAME = "anime_library.json"
    private const val PROGRESS_PREFS = "anime_progress"
    private const val MAX_HISTORY = 120

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    var state by mutableStateOf(LibraryState())
        private set

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        prefs = app.getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
        state = load(app)
        syncProgress()
    }

    fun getProviderSource(): String {
        val app = appContext ?: return "ani-cli"
        val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(app)
        return p.getString("anime_provider_source", "ani-cli") ?: "ani-cli"
    }

    // ---- anime watchlist ----

    fun isBookmarked(slug: String): Boolean =
        state.watchlist.any { it.slug == slug }

    fun toggleBookmark(slug: String, title: String, cover: String?) {
        val existing = state.watchlist.firstOrNull { it.slug == slug }
        val updated = if (existing != null) {
            state.watchlist - existing
        } else {
            listOf(WatchItem(slug, title, cover)) + state.watchlist
        }
        update { it.copy(watchlist = updated) }
    }

    fun removeFromWatchlist(slug: String) {
        update { it.copy(watchlist = it.watchlist.filterNot { item -> item.slug == slug }) }
    }

    // ---- manga watchlist ----

    fun isMangaBookmarked(mangaId: String): Boolean =
        state.mangaWatchlist.any { it.id == mangaId }

    fun toggleMangaBookmark(mangaId: String, title: String, coverUrl: String?) {
        val existing = state.mangaWatchlist.firstOrNull { it.id == mangaId }
        val updated = if (existing != null) {
            state.mangaWatchlist - existing
        } else {
            listOf(MangaWatchItem(mangaId, title, coverUrl)) + state.mangaWatchlist
        }
        update { it.copy(mangaWatchlist = updated) }
    }

    fun removeMangaFromWatchlist(mangaId: String) {
        update { it.copy(mangaWatchlist = it.mangaWatchlist.filterNot { item -> item.id == mangaId }) }
    }

    // ---- anime history ----

    fun historyFor(slug: String): List<HistoryItem> =
        state.history.filter { it.slug == slug }

    fun rememberEpisode(details: AnimeDetails, episode: Episode, mode: String) {
        val existing = state.history.firstOrNull { it.episodeId == episode.id }
        val entry = HistoryItem(
            slug = details.slug,
            title = details.title,
            cover = details.cover,
            animeId = details.animeId,
            episodeNumber = episode.number,
            episodeId = episode.id,
            mode = mode,
            positionMs = existing?.positionMs ?: 0L,
            durationMs = existing?.durationMs ?: 0L,
        )
        val updated = (listOf(entry) + state.history.filterNot { it.episodeId == episode.id })
            .take(MAX_HISTORY)
        update { it.copy(history = updated) }
    }

    fun removeHistory(episodeId: String) {
        update { it.copy(history = it.history.filterNot { h -> h.episodeId == episodeId }) }
        prefs?.edit()?.remove(episodeId)?.apply()
    }

    fun clearHistory() {
        update { it.copy(history = emptyList()) }
        prefs?.edit()?.clear()?.apply()
    }

    // ---- manga history ----

    fun rememberMangaChapter(mangaId: String, title: String, coverUrl: String?, chapterId: String, chapterNumber: String, page: Int = 1, totalPages: Int = 1) {
        val entry = MangaHistoryItem(
            mangaId = mangaId,
            title = title,
            coverUrl = coverUrl,
            chapterId = chapterId,
            chapterNumber = chapterNumber,
            pageNumber = page,
            totalPages = totalPages,
        )
        val updated = (listOf(entry) + state.mangaHistory.filterNot { it.mangaId == mangaId })
            .take(MAX_HISTORY)
        update { it.copy(mangaHistory = updated) }
    }

    fun removeMangaHistory(mangaId: String) {
        update { it.copy(mangaHistory = it.mangaHistory.filterNot { h -> h.mangaId == mangaId }) }
    }

    fun mangaHistoryFor(mangaId: String): MangaHistoryItem? =
        state.mangaHistory.firstOrNull { it.mangaId == mangaId }

    /** Highest chapter number this manga was read up to, used for "read" markers. */
    fun mangaReadUpTo(mangaId: String): Double? =
        mangaHistoryFor(mangaId)?.chapterNumber?.toDoubleOrNull()

    /** Applies positions written by the player while it was in the foreground. */
    fun syncProgress() {
        val prefs = prefs ?: return
        val current = state.history
        if (current.isEmpty()) return

        var changed = false
        val updated = current.map { item ->
            val posKey = "${item.episodeId}_pos"
            val durKey = "${item.episodeId}_dur"
            if (!prefs.contains(posKey)) return@map item

            val p = prefs.getLong(posKey, 0L)
            val d = prefs.getLong(durKey, 0L)
            if (p != item.positionMs || d != item.durationMs) {
                changed = true
                item.copy(positionMs = p, durationMs = d)
            } else {
                item
            }
        }
        if (changed) {
            update { it.copy(history = updated) }
        }
    }

    fun saveProgress(episodeId: String, positionMs: Long, durationMs: Long) {
        if (episodeId.isBlank() || durationMs <= 0) return
        prefs?.edit()
            ?.putLong("${episodeId}_pos", positionMs)
            ?.putLong("${episodeId}_dur", durationMs)
            ?.apply()

        update { current ->
            current.copy(
                history = current.history.map {
                    if (it.episodeId == episodeId) {
                        it.copy(positionMs = positionMs, durationMs = durationMs)
                    } else {
                        it
                    }
                },
            )
        }
    }

    fun lastPositionFor(episodeId: String): Pair<Long, Long> {
        val item = state.history.firstOrNull { it.episodeId == episodeId }
        if (item != null && item.positionMs > 0) {
            return item.positionMs to item.durationMs
        }
        val prefs = prefs ?: return 0L to 0L
        val posKey = "${episodeId}_pos"
        val durKey = "${episodeId}_dur"
        if (prefs.contains(posKey)) {
            return prefs.getLong(posKey, 0L) to prefs.getLong(durKey, 0L)
        }
        return 0L to 0L
    }

    // ---- downloads ----

    fun downloadsFor(episodeId: String): DownloadItem? =
        state.downloads.firstOrNull { it.episodeId == episodeId && it.isComplete }

    fun addDownload(item: DownloadItem) {
        update {
            it.copy(downloads = listOf(item) + it.downloads.filterNot { d -> d.id == item.id })
        }
    }

    fun updateDownload(id: String, transform: (DownloadItem) -> DownloadItem) {
        update { current ->
            current.copy(
                downloads = current.downloads.map { if (it.id == id) transform(it) else it },
            )
        }
    }

    fun removeDownload(id: String) {
        val item = state.downloads.firstOrNull { it.id == id }
        update { it.copy(downloads = it.downloads.filterNot { d -> d.id == id }) }
        if (item != null && item.dir.isNotBlank()) {
            scope.launch { runCatching { File(item.dir).deleteRecursively() } }
        }
    }

    // ---- persistence ----

    private fun update(transform: (LibraryState) -> LibraryState) {
        synchronized(this) {
            state = transform(state)
            val snapshot = state
            scope.launch { save(snapshot) }
        }
    }

    private fun load(context: Context): LibraryState {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return LibraryState()
        return runCatching {
            val root = JSONObject(file.readText())
            LibraryState(
                watchlist = root.optJSONArray("watchlist").mapObjects { obj ->
                    WatchItem(
                        slug = obj.optString("slug"),
                        title = obj.optString("title"),
                        cover = obj.optString("cover").takeIf { it.isNotBlank() },
                        addedAt = obj.optLong("addedAt"),
                    )
                },
                history = root.optJSONArray("history").mapObjects { obj ->
                    HistoryItem(
                        slug = obj.optString("slug"),
                        title = obj.optString("title"),
                        cover = obj.optString("cover").takeIf { it.isNotBlank() },
                        animeId = obj.optString("animeId"),
                        episodeNumber = obj.optString("episodeNumber"),
                        episodeId = obj.optString("episodeId"),
                        mode = obj.optString("mode", "sub"),
                        positionMs = obj.optLong("positionMs"),
                        durationMs = obj.optLong("durationMs"),
                        updatedAt = obj.optLong("updatedAt"),
                    )
                },
                downloads = root.optJSONArray("downloads").mapObjects { obj ->
                    DownloadItem(
                        id = obj.optString("id"),
                        slug = obj.optString("slug"),
                        title = obj.optString("title"),
                        cover = obj.optString("cover").takeIf { it.isNotBlank() },
                        animeId = obj.optString("animeId"),
                        episodeNumber = obj.optString("episodeNumber"),
                        episodeId = obj.optString("episodeId"),
                        mode = obj.optString("mode", "sub"),
                        quality = obj.optInt("quality", 720),
                        status = obj.optString("status", DownloadStatus.FAILED),
                        progress = obj.optInt("progress"),
                        bytes = obj.optLong("bytes"),
                        dir = obj.optString("dir"),
                        subtitles = obj.optJSONArray("subtitles").mapObjects { sub ->
                            LocalSubtitle(sub.optString("label"), sub.optString("path"))
                        },
                        createdAt = obj.optLong("createdAt"),
                        error = obj.optString("error").takeIf { it.isNotBlank() },
                    )
                },
                mangaWatchlist = root.optJSONArray("mangaWatchlist").mapObjects { obj ->
                    MangaWatchItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                        addedAt = obj.optLong("addedAt"),
                    )
                },
                mangaHistory = root.optJSONArray("mangaHistory").mapObjects { obj ->
                    MangaHistoryItem(
                        mangaId = obj.optString("mangaId"),
                        title = obj.optString("title"),
                        coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                        chapterId = obj.optString("chapterId"),
                        chapterNumber = obj.optString("chapterNumber"),
                        pageNumber = obj.optInt("pageNumber", 1),
                        totalPages = obj.optInt("totalPages", 1),
                        updatedAt = obj.optLong("updatedAt"),
                    )
                },
            )
        }.getOrElse { LibraryState() }
    }

    private fun save(snapshot: LibraryState) {
        val context = appContext ?: return
        runCatching {
            val root = JSONObject()
            root.put("watchlist", JSONArray().apply {
                snapshot.watchlist.forEach { w ->
                    put(JSONObject().apply {
                        put("slug", w.slug)
                        put("title", w.title)
                        put("cover", w.cover ?: "")
                        put("addedAt", w.addedAt)
                    })
                }
            })
            root.put("history", JSONArray().apply {
                snapshot.history.forEach { h ->
                    put(JSONObject().apply {
                        put("slug", h.slug)
                        put("title", h.title)
                        put("cover", h.cover ?: "")
                        put("animeId", h.animeId)
                        put("episodeNumber", h.episodeNumber)
                        put("episodeId", h.episodeId)
                        put("mode", h.mode)
                        put("positionMs", h.positionMs)
                        put("durationMs", h.durationMs)
                        put("updatedAt", h.updatedAt)
                    })
                }
            })
            root.put("downloads", JSONArray().apply {
                snapshot.downloads.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("slug", d.slug)
                        put("title", d.title)
                        put("cover", d.cover ?: "")
                        put("animeId", d.animeId)
                        put("episodeNumber", d.episodeNumber)
                        put("episodeId", d.episodeId)
                        put("mode", d.mode)
                        put("quality", d.quality)
                        put("status", d.status)
                        put("progress", d.progress)
                        put("bytes", d.bytes)
                        put("dir", d.dir)
                        put("subtitles", JSONArray().apply {
                            d.subtitles.forEach { s ->
                                put(JSONObject().put("label", s.label).put("path", s.path))
                            }
                        })
                        put("createdAt", d.createdAt)
                        put("error", d.error ?: "")
                    })
                }
            })
            root.put("mangaWatchlist", JSONArray().apply {
                snapshot.mangaWatchlist.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("title", m.title)
                        put("coverUrl", m.coverUrl ?: "")
                        put("addedAt", m.addedAt)
                    })
                }
            })
            root.put("mangaHistory", JSONArray().apply {
                snapshot.mangaHistory.forEach { h ->
                    put(JSONObject().apply {
                        put("mangaId", h.mangaId)
                        put("title", h.title)
                        put("coverUrl", h.coverUrl ?: "")
                        put("chapterId", h.chapterId)
                        put("chapterNumber", h.chapterNumber)
                        put("pageNumber", h.pageNumber)
                        put("totalPages", h.totalPages)
                        put("updatedAt", h.updatedAt)
                    })
                }
            })

            File(context.filesDir, FILE_NAME).writeText(root.toString())
        }
    }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val list = mutableListOf<T>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            list.add(transform(obj))
        }
        return list
    }
}