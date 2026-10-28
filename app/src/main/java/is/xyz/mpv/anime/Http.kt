package `is`.xyz.mpv.anime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Small OkHttp wrapper used by both providers. All calls run off the main thread.
 */
object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES))
        .build()

    suspend fun get(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply { if (referer != null) header("Referer", referer) }
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (body.contains("Just a moment", ignoreCase = true)) {
                    throw IOException("Cloudflare challenge (HTTP ${response.code})")
                }
                throw IOException("HTTP ${response.code} for ${request.url.host}")
            }
            body
        }
    }

    suspend fun postJson(url: String, json: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(json.toRequestBody(jsonMedia))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful)
                throw IOException("HTTP ${response.code} from ${request.url.host}")
            body
        }
    }

    /** Streams [url] into [dest] with automatic referer fallback and retries on 403 or socket resets. */
    suspend fun download(url: String, referer: String?, dest: File): Long = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        val referersToTry = listOfNotNull(referer, "https://megacloud.tv/", "https://hianime.at/", null).distinct()

        for (ref in referersToTry) {
            for (attempt in 1..2) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .apply { if (ref != null) header("Referer", ref) }
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful)
                            throw IOException("HTTP ${response.code} for ${request.url.host}")
                        val body = response.body ?: throw IOException("Empty response")
                        dest.parentFile?.mkdirs()
                        body.byteStream().use { input ->
                            dest.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                        }
                        return@withContext dest.length()
                    }
                } catch (e: Exception) {
                    lastError = e
                    delay(150L * attempt)
                }
            }
        }
        throw lastError ?: IOException("Failed to download segment after retries")
    }
}

/** Returns "scheme://host[:port]/" or null if [url] is not an absolute http(s) URL. */
fun originOf(url: String): String? {
    return try {
        val uri = URI(url)
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        if (!scheme.equals("http", true) && !scheme.equals("https", true)) return null
        buildString {
            append(scheme).append("://").append(host)
            if (uri.port > 0) append(':').append(uri.port)
            append('/')
        }
    } catch (_: Exception) {
        null
    }
}

/** Strips a HiAnime/AniList title down to something AniList search likes. */
fun cleanTitle(title: String): String = title
    .replace(Regex("\\(\\s*(?:sub|dub|tv|uncensored)\\s*\\)", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+"), " ")
    .trim()