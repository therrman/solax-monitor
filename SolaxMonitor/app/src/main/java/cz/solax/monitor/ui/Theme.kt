package cz.solax.monitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Solární žlutá jako akcent, tmavé pozadí s modrým nádechem. */
object SolaxColors {
    val Sun = Color(0xFFF5B429)
    val Charge = Color(0xFF4ADE80)
    val Discharge = Color(0xFFFB923C)
    val Export = Color(0xFF38BDF8)
    val Import = Color(0xFFF87171)
    val Warn = Color(0xFFFBBF24)
    val Bad = Color(0xFFEF4444)
    val Muted = Color(0xFF94A3B8)

    val DarkBg = Color(0xFF0F1720)
    val DarkCard = Color(0xFF1A2430)
}

private val DarkScheme = darkColorScheme(
    primary = SolaxColors.Sun,
    onPrimary = Color(0xFF1A1200),
    secondary = SolaxColors.Export,
    background = SolaxColors.DarkBg,
    onBackground = Color(0xFFE2E8F0),
    surface = SolaxColors.DarkCard,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF243040),
    onSurfaceVariant = SolaxColors.Muted,
    error = SolaxColors.Bad,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB07C00),
    secondary = Color(0xFF0369A1),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    error = SolaxColors.Bad,
)

@Composable
fun SolaxTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
