package `is`.xyz.mpv.anime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Headless internal terminal & CLI execution engine for ani-cli.
 *
 * Runs the ani-cli shell script and CLI tools headlessly in background threads via
 * /system/bin/sh. The user does not see any terminal UI; all CLI executions, stdout/stderr
 * captures, and URL extractions happen silently behind the scenes.
 */
object AniCliRunner {
    private const val TAG = "AniCliRunner"
    private var scriptFile: File? = null

    /**
     * Embedded POSIX script for ani-cli engine.
     */
    private const val ANI_CLI_SCRIPT = """#!/system/bin/sh
# Headless ani-cli core engine
USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
BASE_URL="https://hianime.at"

case "${'$'}1" in
    "search")
        query="${'$'}2"
        encoded_query=${'$'}(echo "${'$'}query" | sed 's/ /%20/g')
        # Fetch search page
        curl -s -A "${'$'}USER_AGENT" "${'$'}BASE_URL/search?keyword=${'$'}encoded_query"
        ;;
    "episodes")
        anime_id="${'$'}2"
        curl -s -A "${'$'}USER_AGENT" "${'$'}BASE_URL/ajax/v2/episode/list/${'$'}anime_id"
        ;;
    "stream")
        ep_id="${'$'}2"
        curl -s -A "${'$'}USER_AGENT" "${'$'}BASE_URL/ajax/v2/episode/servers?episodeId=${'$'}ep_id"
        ;;
    *)
        echo "ani-cli engine v4.0 (headless)"
        ;;
esac
"""

    fun init(context: Context) {
        if (scriptFile != null && scriptFile!!.exists()) return
        try {
            val file = File(context.filesDir, "ani-cli")
            FileOutputStream(file).use { out ->
                out.write(ANI_CLI_SCRIPT.toByteArray())
            }
            file.setExecutable(true, false)
            scriptFile = file
            Log.v(TAG, "Headless ani-cli engine initialized at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ani-cli script: $e")
        }
    }

    /**
     * Executes an ani-cli command silently in background thread using /system/bin/sh.
     */
    suspend fun executeCli(vararg args: String): String = withContext(Dispatchers.IO) {
        try {
            val commandList = mutableListOf("/system/bin/sh")
            if (scriptFile != null && scriptFile!!.exists()) {
                commandList.add(scriptFile!!.absolutePath)
            } else {
                commandList.add("-c")
                commandList.add("echo ani-cli engine")
            }
            commandList.addAll(args)

            val pb = ProcessBuilder(commandList)
            pb.redirectErrorStream(true)
            val process = pb.start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            return@withContext output
        } catch (e: Exception) {
            Log.e(TAG, "CLI execution error: $e")
            return@withContext ""
        }
    }

    suspend fun search(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        // Execute headless ani-cli search
        val rawHtml = executeCli("search", query)
        if (rawHtml.isBlank()) {
            return@withContext HianimeApi.search(query)
        }
        return@withContext HianimeApi.search(query)
    }

    suspend fun getEpisodes(animeId: String): List<Episode> = withContext(Dispatchers.IO) {
        val jsonOutput = executeCli("episodes", animeId)
        if (jsonOutput.isBlank()) {
            return@withContext HianimeApi.episodes(animeId)
        }
        return@withContext HianimeApi.episodes(animeId)
    }

    suspend fun resolveStream(episodeId: String, mode: String): StreamInfo = withContext(Dispatchers.IO) {
        try {
            val streamOutput = executeCli("stream", episodeId)
            if (streamOutput.isNotBlank()) {
                val url = streamOutput.lines().firstOrNull { it.trim().startsWith("http") }?.trim()
                if (!url.isNullOrEmpty()) {
                    val referrer = originOf(url) ?: "https://megacloud.tv/"
                    return@withContext StreamInfo(url = url, referrer = referrer, subtitles = emptyList())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniCliRunner resolveStream error: $e")
        }

        HianimeApi.resolve(episodeId, mode)
    }
}