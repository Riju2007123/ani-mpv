package `is`.xyz.mpv.anime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import `is`.xyz.mpv.MainActivity
import `is`.xyz.mpv.preferences.PreferenceActivity

/**
 * Launcher entry point of the app: the anime browser. The classic mpv file
 * picker stays reachable through the "Files" action in the bottom bar.
 */
class AnimeHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Library.init(this)
        DownloadService.maybeResume(this)
        enableEdgeToEdge()

        setContent {
            AnimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    AnimeApp(
                        onOpenFiles = {
                            startActivity(Intent(this, MainActivity::class.java))
                        },
                        onOpenSettings = {
                            startActivity(Intent(this, PreferenceActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The player writes progress while it is in the foreground.
        Library.syncProgress()
    }
}
