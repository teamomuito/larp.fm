package io.github.teamomuito.larpfm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always pink: no dynamic (wallpaper-based) colors.
private val LightColors = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF8E4A67),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3A0722),
    tertiary = Color(0xFF9C4146),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDADA),
    onTertiaryContainer = Color(0xFF40000A),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    surfaceDim = Color(0xFFE6D6D9),
    surfaceBright = Color(0xFFFFF8F9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFAEAED),
    surfaceContainerHigh = Color(0xFFF4E4E7),
    surfaceContainerHighest = Color(0xFFEFDFE2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB0CB),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFFFFB0CB),
    onSecondary = Color(0xFF561D38),
    secondaryContainer = Color(0xFF71334F),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFFFFB3B5),
    onTertiary = Color(0xFF5F131C),
    tertiaryContainer = Color(0xFF7E2A31),
    onTertiaryContainer = Color(0xFFFFDADA),
    background = Color(0xFF191113),
    onBackground = Color(0xFFEFDFE2),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDFE2),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C91),
    outlineVariant = Color(0xFF514347),
    surfaceDim = Color(0xFF191113),
    surfaceBright = Color(0xFF413739),
    surfaceContainerLowest = Color(0xFF140C0E),
    surfaceContainerLow = Color(0xFF22191C),
    surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3C3235),
)

@Composable
fun LarpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
