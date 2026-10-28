package `is`.xyz.mpv.anime

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentPink = Color(0xFFFF4D6D)
val AccentViolet = Color(0xFF9B6BFF)
val SubCyan = Color(0xFF22D3EE)
val DubAmber = Color(0xFFFFB020)

private val AnimeColorScheme = darkColorScheme(
    primary = AccentPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1220),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = AccentViolet,
    onSecondary = Color.White,
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFF4F4FA),
    surface = Color(0xFF14141F),
    onSurface = Color(0xFFF4F4FA),
    surfaceVariant = Color(0xFF222233),
    onSurfaceVariant = Color(0xFFB6B6C9),
    outline = Color(0xFF3A3A4E),
)

@Composable
fun AnimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AnimeColorScheme, content = content)
}
