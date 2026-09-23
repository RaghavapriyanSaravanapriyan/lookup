package dev.lookup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * lookup terminal theme: pure black & white, always dark.
 * there is no light mode. light mode is for people who look up.
 * colour lives only inside the live charts + the warning bar —
 * everything else is ink, paper-inverted, and hairlines.
 */
private val TerminalScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF17181C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF17181C),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF131417),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0B0C0E),
    surfaceContainer = Color(0xFF131417),
    surfaceContainerHigh = Color(0xFF1A1C20),
    surfaceContainerHighest = Color(0xFF23252B),
    outline = Color(0xFF26292F),
    outlineVariant = Color(0xFF1A1C20),
    error = Color(0xFFFFFFFF),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF1A1C20),
    onErrorContainer = Color(0xFFFFFFFF),
)

private val Mono = FontFamily.Monospace

private val TerminalTypography = Typography(
    displayLarge = TextStyle(fontFamily = Mono, fontSize = 52.sp, lineHeight = 52.sp),
    displayMedium = TextStyle(fontFamily = Mono, fontSize = 40.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = Mono, fontSize = 28.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Mono, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun LookupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalScheme,
        typography = TerminalTypography,
        content = content,
    )
}
