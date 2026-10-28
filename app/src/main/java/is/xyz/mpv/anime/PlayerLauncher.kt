package `is`.xyz.mpv.anime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import `is`.xyz.mpv.MPVActivity
import java.io.File

private const val TAG = "AnimePlayer"

/** Plays a freshly resolved stream, resuming from the saved position if any. */
fun launchStream(
    context: Context,
    title: String,
    episodeNumber: String,
    episodeId: String,
    stream: StreamInfo,
    startMs: Long = 0L,
) {
    val displayTitle = buildTitle(title, episodeNumber)
    val intent = Intent(context, MPVActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(stream.url)
        putExtra("title", displayTitle)
        putExtra("anime_track", episodeId)
        stream.referrer?.let { putExtra("referrer", it) }
        if (startMs > 0) putExtra("position", startMs.toInt())
        addSubtitles(stream.subtitles.map { Pair(it.label, Uri.parse(it.url)) })
    }
    start(context, intent, displayTitle)
}

/** Plays an episode that was downloaded for offline use. */
fun launchLocalDownload(context: Context, item: DownloadItem, startMs: Long = 0L) {
    val playlist = File(item.dir, "index.m3u8")
    if (!playlist.exists()) {
        Toast.makeText(context, "Downloaded file is missing", Toast.LENGTH_LONG).show()
        return
    }
    val displayTitle = buildTitle(item.title, item.episodeNumber)
    val intent = Intent(context, MPVActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.fromFile(playlist)
        putExtra("title", displayTitle)
        putExtra("anime_track", item.episodeId)
        if (startMs > 0) putExtra("position", startMs.toInt())
        addSubtitles(item.subtitles.map { Pair(it.label, Uri.fromFile(File(it.path))) })
    }
    start(context, intent, displayTitle)
}

/** Re-resolves a history entry and plays it from where the user stopped. */
suspend fun resumeFromHistory(context: Context, item: HistoryItem) {
    val stream = AnimeRepository.resolve(item.episodeId, item.mode)
    launchStream(
        context = context,
        title = item.title,
        episodeNumber = item.episodeNumber,
        episodeId = item.episodeId,
        stream = stream,
        startMs = resumePositionFor(item.episodeId),
    )
}

/** Resume offset that avoids restarting from zero or from the credits. */
fun resumePositionFor(episodeId: String): Long {
    val (position, duration) = Library.lastPositionFor(episodeId)
    if (duration <= 0L || position < 5_000L) return 0L
    if (position > duration * 0.94) return 0L
    return (position - 2_000L).coerceAtLeast(0L)
}

private fun buildTitle(title: String, episodeNumber: String): String =
    if (episodeNumber.isBlank()) title else "$title — Episode $episodeNumber"

private fun Intent.addSubtitles(subtitles: List<Pair<String, Uri>>) {
    if (subtitles.isEmpty()) return
    val all = subtitles.map { it.second }.toTypedArray()
    putExtra("subs", all)
    putExtra("subs.enable", all)
}

private fun start(context: Context, intent: Intent, title: String) {
    Log.v(TAG, "Playing $title (track=${intent.getStringExtra("anime_track")})")
    try {
        context.startActivity(intent)
    } catch (t: Throwable) {
        Log.e(TAG, "Could not start mpv", t)
        Toast.makeText(context, "Could not start the player", Toast.LENGTH_LONG).show()
    }
}
