package za.co.eci.slipmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EciColors = lightColorScheme(
    primary = Color(0xFF0B67B7),
    onPrimary = Color.White,
    secondary = Color(0xFF7E43C7),
    background = Color(0xFFF4F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF4FA),
    onBackground = Color(0xFF10243E),
    onSurface = Color(0xFF10243E),
    onSurfaceVariant = Color(0xFF52657A),
    error = Color(0xFFC62828)
)

@Composable
fun ECISlipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EciColors, content = content)
}
