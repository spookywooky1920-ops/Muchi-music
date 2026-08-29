package app.muchi.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GreenBg = Color(0xFF0B1512)
val GreenSurface = Color(0xFF15201C)
val GreenCard = Color(0xFF1C2A25)
val Lime = Color(0xFFC8F542)
val Cream = Color(0xFFE8EDE9)
val Mute = Color(0xFF9BB0A8)

private val scheme = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF10210C),
    background = GreenBg,
    onBackground = Cream,
    surface = GreenSurface,
    onSurface = Cream,
    surfaceVariant = GreenCard,
    onSurfaceVariant = Mute,
    secondary = Color(0xFFE6E2A8),
    outline = Color(0xFF2E433C),
)

@Composable
fun MuchiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
