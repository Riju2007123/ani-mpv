package `is`.xyz.mpv

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Rational
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.buildPictureInPictureParams
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import is.xyz.mpv.databinding.ActivityPlayerBinding

/**
 * Modern video player activity.
 * Hosts an mpv surface (via AndroidView) underneath a Compose control overlay.
 * Supports Picture-in-Picture and background audio via the existing service.
 */
class VideoPlayerActivity : ComponentActivity() {

    private lateinit var binding: ActivityPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + keep screen on while playing
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContent {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) {
                // The mpv video surface is placed by AndroidView below this Compose layer.
                // Replace the placeholder below with your mpv player view.
                PlayerPlaceholder(
                    onPlayPause = {},
                    onSeek = {},
                    onSpeed = {},
                    onAudioTrack = {},
                    onSubtitle = {},
                    onClose = { finish() }
                )
            }
        }

        setContentView(binding.root)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPictureInPicture()
    }

    private fun enterPictureInPicture() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val width = binding.videoSurface.width
            val height = binding.videoSurface.height
            val ratio = if (width > 0 && height > 0) Rational(width, height) else Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
                .build()
            enterPictureInPicture(params)
        }
    }
}

/**
 * Temporary placeholder so the file compiles standalone.
 * Wire this to your existing MPV surface / mpv-android view in the real implementation.
 */
@Composable
fun PlayerPlaceholder(
    onPlayPause: () -> Unit,
    onSeek: (progress: Float) -> Unit,
    onSpeed: () -> Unit,
    onAudioTrack: () -> Unit,
    onSubtitle: () -> Unit,
    onClose: () -> Unit,
) {
    is.xyz.mpv.ModernPlayerControls(
        isPlaying = true,
        positionMs = 0,
        durationMs = 0,
        onPlayPause = onPlayPause,
        onSeek = onSeek,
        onSpeed = onSpeed,
        onAudioTrack = onAudioTrack,
        onSubtitle = onSubtitle,
        onClose = onClose
    )
}
