package za.co.eci.slipmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EciColors = darkColorScheme(
    primary = Color(0xFFFFC107),
    onPrimary = Color(0xFF171717),
    secondary = Color(0xFFFFD54F),
    background = Color(0xFF101214),
    surface = Color(0xFF191C20),
    surfaceVariant = Color(0xFF24282D),
    onBackground = Color(0xFFF4F4F4),
    onSurface = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFFCCD0D5),
    error = Color(0xFFFF6B6B)
)

@Composable
fun ECISlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EciColors, content = content)
}
