package svoboda3.runet.abonent0007.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    secondary = AccentBlue,
    background = Bg,
    surface = Surface,
    surfaceVariant = Elevated,
    onPrimary = Bg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed,
    outline = Border,
)

@Composable
fun SvobodaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
