package dev.lookup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E6BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001B42),
    secondary = Color(0xFF585E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE1F5),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFFE58E00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDFAB),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFFAFBFF),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFAFBFF),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB0C4FF),
    onPrimary = Color(0xFF00296C),
    primaryContainer = Color(0xFF003EA5),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFC0C5D8),
    onSecondary = Color(0xFF292F40),
    secondaryContainer = Color(0xFF3F4657),
    onSecondaryContainer = Color(0xFFDCE1F5),
    tertiary = Color(0xFFFFB84D),
    onTertiary = Color(0xFF492900),
    tertiaryContainer = Color(0xFF6A3F00),
    onTertiaryContainer = Color(0xFFFFDFAB),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun LookupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
