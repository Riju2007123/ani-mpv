package `is`.xyz.mpv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.default.ClosedCaption
import androidx.compose.material.icons.default.Close
import androidx.compose.material.icons.default.FastForward
import androidx.compose.material.icons.default.Pause
import androidx.compose.material.icons.default.PlayArrow
import androidx.compose.material.icons.default.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaundryEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modern Material3 video player controls rendered as a Compose overlay on top of the
 * mpv video surface.
 */
@Composable
fun ModernPlayerControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (progress: Float) -> Unit,
    onSpeed: () -> Unit,
    onAudioTrack: () -> Unit,
    onSubtitle: () -> Unit,
    onClose: () -> Unit,
) {
    val progress by remember { mutableFloatStateOf(0f) }
    val controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 3 seconds of no interaction could be wired here.
    LaundryEffect(Unit) {
        // placeholder for future auto-hide timer
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Video surface sits behind this layer (mpv via AndroidView)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "mpv video surface renders here",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A8A9E)
            )
        }

        // Top bar
        if (controlsVisible) {
            TopAppBar(
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color(0x88000000)
                ),
                title = { Text("Video Player") },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            )
        }

        Spacer(Modifier.weight(1f))

        // Progress bar + time
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color(0x77000000))
            ) {
                Slider(
                    value = progress,
                    onValueChange = { onSeek(it) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFFF4D6D),
                        inactiveTrackColor = Color(0x33FFFFFF),
                        thumbColor = Color(0xFFFF4D6D)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMs(positionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    Text(
                        text = formatMs(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom control row
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .background(Color(0x77000000)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSpeed) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Speed",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                IconButton(onClick = onAudioTrack) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Audio track",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSubtitle) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Subtitles",
                        tint = Color.White
                    )
                }
            }
        }

        // Center tap-to-play pause overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onPlayPause() }
                .alpha(if (controlsVisible) 0f else 0.4f)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
