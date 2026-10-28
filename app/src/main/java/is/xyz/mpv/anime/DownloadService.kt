package `is`.xyz.mpv.anime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Foreground service that downloads an episode for offline playback. Downloads
 * keep running while the app is in the background and report progress into
 * [Library] so the "My List → Downloads" screen stays live.
 */
class DownloadService : Service() {
    companion object {
        private const val ACTION_START = "is.xyz.mpv.anime.DOWNLOAD_START"
        private const val ACTION_CANCEL = "is.xyz.mpv.anime.DOWNLOAD_CANCEL"
        private const val CHANNEL_ID = "anime_downloads"
        private const val NOTIFICATION_ID = 42

        /** Downloads owned by this process, so a restart only clears stale jobs. */
        private val activeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun enqueue(
            context: Context,
            details: AnimeDetails,
            episode: Episode,
            mode: String,
            quality: Int,
            stream: StreamInfo,
        ) {
            Library.init(context)
            val id = "${episode.id}_$mode"
            val dir = File(
                context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads"),
                id,
            )

            Library.addDownload(
                DownloadItem(
                    id = id,
                    slug = details.slug,
                    title = details.title,
                    cover = details.cover,
                    animeId = details.animeId,
                    episodeNumber = episode.number,
                    episodeId = episode.id,
                    mode = mode,
                    quality = quality,
                    status = DownloadStatus.QUEUED,
                    dir = dir.absolutePath,
                ),
            )

            val subtitlesJson = JSONArray().apply {
                stream.subtitles.forEach { sub ->
                    put(
                        JSONObject()
                            .put("label", sub.label)
                            .put("url", sub.url)
                            .put("default", sub.isDefault),
                    )
                }
            }

            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START
                putExtra("id", id)
                putExtra("url", stream.url)
                putExtra("referrer", stream.referrer)
                putExtra("quality", quality)
                putExtra("dir", dir.absolutePath)
                putExtra("episodeId", episode.id)
                putExtra("subtitles", subtitlesJson.toString())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, id: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra("id", id)
            }
            context.startService(intent)
        }

        fun maybeResume(context: Context) {
            val pending = Library.state.downloads.filter {
                it.isActive && it.id !in activeIds
            }
            if (pending.isEmpty()) return
            // After a process restart the jobs are gone; mark them failed so the
            // user can start them again instead of staring at a stuck bar.
            pending.forEach { item ->
                Library.updateDownload(item.id) {
                    it.copy(status = DownloadStatus.CANCELED, error = "Interrupted")
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDownload(intent)
            ACTION_CANCEL -> cancelDownload(intent.getStringExtra("id"))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private fun startDownload(intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val url = intent.getStringExtra("url") ?: return
        val referrer = intent.getStringExtra("referrer")
        val quality = intent.getIntExtra("quality", 720)
        val dir = File(intent.getStringExtra("dir") ?: return)
        val subtitles = parseSubtitles(intent.getStringExtra("subtitles"))

        startForegroundCompat(buildNotification("Preparing download…", 0, null))

        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            activeIds += id
            Library.updateDownload(id) {
                it.copy(status = DownloadStatus.DOWNLOADING, progress = 0, error = null)
            }

            var lastNotified = -1
            try {
                val result = HlsDownloader.download(
                    url = url,
                    referrer = referrer,
                    quality = quality,
                    dir = dir,
                    subtitles = subtitles,
                ) { percent, bytes ->
                    Library.updateDownload(id) {
                        it.copy(
                            progress = percent,
                            bytes = bytes,
                            status = DownloadStatus.DOWNLOADING,
                        )
                    }
                    if (percent - lastNotified >= 5 || percent == 100) {
                        lastNotified = percent
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                Library.state.downloads.firstOrNull { it.id == id }?.let {
                                    "ani-cli · ${it.title} Ep ${it.episodeNumber}"
                                } ?: "Downloading via ani-cli…",
                                percent,
                                id,
                            ),
                        )
                    }
                }

                Library.updateDownload(id) {
                    it.copy(
                        status = DownloadStatus.COMPLETE,
                        progress = 100,
                        bytes = result.bytes,
                        dir = result.playlist.parentFile?.absolutePath ?: it.dir,
                        subtitles = result.subtitles,
                        error = null,
                    )
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification("Download complete", 100, null),
                )
            } catch (canceled: CancellationException) {
                dir.deleteRecursively()
                Library.updateDownload(id) {
                    it.copy(status = DownloadStatus.CANCELED, progress = 0, error = "Canceled")
                }
                throw canceled
            } catch (t: Throwable) {
                dir.deleteRecursively()
                Library.updateDownload(id) {
                    it.copy(
                        status = DownloadStatus.FAILED,
                        error = t.userMessage(),
                    )
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification("Download failed", 0, null),
                )
            } finally {
                jobs.remove(id)
                activeIds -= id
                if (jobs.isEmpty()) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun cancelDownload(id: String?) {
        if (id == null) return
        jobs.remove(id)?.cancel()
        Library.updateDownload(id) {
            it.copy(status = DownloadStatus.CANCELED, progress = 0, error = "Canceled")
        }
        if (jobs.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun parseSubtitles(raw: String?): List<Subtitle> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val url = obj.optString("url")
                if (url.isBlank()) return@mapNotNull null
                Subtitle(
                    label = obj.optString("label", "Subtitle"),
                    url = url,
                    isDefault = obj.optBoolean("default", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(title: String, progress: Int, downloadId: String?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AnimeHomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AniMPV")
            .setContentText(title)
            .setContentIntent(openIntent)
            .setOngoing(progress in 1..99)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 1..99) {
            builder.setProgress(100, progress, false)
        } else if (progress == 100) {
            builder.setProgress(0, 0, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (downloadId != null) {
            val cancelIntent = PendingIntent.getForegroundService(
                this,
                1,
                Intent(this, DownloadService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra("id", downloadId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Cancel", cancelIntent)
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Anime downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Offline episode downloads"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
