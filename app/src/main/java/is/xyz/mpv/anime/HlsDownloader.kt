package `is`.xyz.mpv.anime

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Turns a remote HLS stream into a local one: downloads segments in parallel (8x concurrent streams),
 * writes a playlist that references the local files, and stores the subtitle tracks.
 */
object HlsDownloader {
    data class Result(
        val playlist: File,
        val subtitles: List<LocalSubtitle>,
        val bytes: Long,
    )

    private data class Variant(val height: Int, val bandwidth: Long, val url: String)
    private data class SegmentTask(val index: Int, val segmentUrl: String, val outputFile: File)

    suspend fun download(
        url: String,
        referrer: String?,
        quality: Int,
        dir: File,
        subtitles: List<Subtitle>,
        onProgress: (percent: Int, bytes: Long) -> Unit,
    ): Result = coroutineScope {
        dir.mkdirs()
        val masterUrl = url
        val master = Http.get(masterUrl, referrer)

        val variants = parseVariants(master, masterUrl)
        val mediaUrl: String
        val mediaText: String
        if (variants.isNotEmpty()) {
            val chosen = variants.filter { it.height <= quality }.maxByOrNull { it.height }
                ?: variants.minByOrNull { it.height }!!
            mediaUrl = chosen.url
            mediaText = Http.get(mediaUrl, referrer)
        } else {
            mediaUrl = masterUrl
            mediaText = master
        }

        val lines = mediaText.lines()
        val totalSegments = lines.count { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (totalSegments == 0) throw IOException("Playlist has no segments")

        val outputLines = ArrayList<String>(lines.size)
        val segmentTasks = ArrayList<SegmentTask>(totalSegments)
        var index = 0

        for (raw in lines) {
            coroutineContext.ensureActive()
            val line = raw.trim()
            when {
                line.isEmpty() -> outputLines += ""

                line.startsWith("#EXT-X-KEY") -> {
                    outputLines += rewriteUriAttribute(line, mediaUrl)
                }

                line.startsWith("#EXT-X-MAP") -> {
                    val mapUrl = extractUri(line)?.let { resolve(mediaUrl, it) }
                    if (mapUrl != null) {
                        val mapFile = File(dir, "init_${index}.mp4")
                        Http.download(mapUrl, referrer, mapFile)
                        outputLines += line.replace(
                            Regex("URI=\"[^\"]+\""),
                            "URI=\"${mapFile.name}\"",
                        )
                    } else {
                        outputLines += line
                    }
                }

                line.startsWith("#") -> outputLines += line

                else -> {
                    val segmentUrl = resolve(mediaUrl, line)
                    val segmentFile = File(dir, "seg_%05d.ts".format(index))
                    segmentTasks += SegmentTask(index, segmentUrl, segmentFile)
                    outputLines += segmentFile.name
                    index++
                }
            }
        }

        // High-speed parallel segment downloads using 12 concurrent connections
        val semaphore = Semaphore(12)
        val completedCount = AtomicInteger(0)
        val totalBytesAcc = AtomicLong(0L)

        val jobs = segmentTasks.map { task ->
            async {
                semaphore.withPermit {
                    coroutineContext.ensureActive()
                    val downloadedBytes = Http.download(task.segmentUrl, referrer, task.outputFile)
                    val currentBytes = totalBytesAcc.addAndGet(downloadedBytes)
                    val finished = completedCount.incrementAndGet()

                    val percent = finished * 100 / totalSegments
                    onProgress(percent.coerceIn(0, 100), currentBytes)
                }
            }
        }

        jobs.forEach { it.await() }

        if (outputLines.none { it.startsWith("#EXT-X-ENDLIST") }) {
            outputLines += "#EXT-X-ENDLIST"
        }

        val playlist = File(dir, "index.m3u8")
        playlist.writeText(outputLines.joinToString("\n"))

        val localSubs = mutableListOf<LocalSubtitle>()
        subtitles.forEachIndexed { subIndex, subtitle ->
            val extension = subtitle.url.substringBefore('?').substringAfterLast('.', "vtt")
                .takeIf { it.length in 2..4 } ?: "vtt"
            val subFile = File(dir, "sub_${subIndex}.$extension")
            runCatching { Http.download(subtitle.url, referrer, subFile) }
                .onSuccess { localSubs += LocalSubtitle(subtitle.label, subFile.absolutePath) }
        }

        val finalBytes = totalBytesAcc.get()
        onProgress(100, finalBytes)
        Result(playlist, localSubs, finalBytes)
    }

    private fun parseVariants(manifest: String, baseUrl: String): List<Variant> {
        val result = mutableListOf<Variant>()
        val lines = manifest.lines()
        var lastHeight = 0
        var lastBandwidth = 0L

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                lastHeight = extractResolutionHeight(trimmed)
                lastBandwidth = extractAttribute(trimmed, "BANDWIDTH")?.toLongOrNull() ?: 0L
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val url = resolve(baseUrl, trimmed)
                result += Variant(lastHeight, lastBandwidth, url)
                lastHeight = 0
                lastBandwidth = 0L
            }
        }
        return result
    }

    private fun extractResolutionHeight(line: String): Int {
        val match = Regex("RESOLUTION=\\d+x(\\d+)").find(line)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun extractAttribute(line: String, attr: String): String? {
        val match = Regex("$attr=([^,\\s]+)").find(line)
        return match?.groupValues?.get(1)?.replace("\"", "")
    }

    private fun extractUri(line: String): String? {
        val match = Regex("URI=\"([^\"]+)\"").find(line)
        return match?.groupValues?.get(1)
    }

    private fun rewriteUriAttribute(line: String, baseUrl: String): String {
        val oldUri = extractUri(line) ?: return line
        val newUri = resolve(baseUrl, oldUri)
        return line.replace("URI=\"$oldUri\"", "URI=\"$newUri\"")
    }

    private fun resolve(baseUrl: String, relative: String): String {
        return try {
            URI(baseUrl).resolve(relative).toString()
        } catch (_: Exception) {
            relative
        }
    }
}