package com.lielu.keep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.lielu.keep.data.settings.SettingsRepository
import com.lielu.keep.data.settings.ThemeMode

// Palette neutre du modèle (violet doux). Les couleurs vivent ici et nulle part ailleurs : un écran ne code
// pas de couleur en dur. `BackgroundLight`/`BackgroundDark` doivent suivre `window_background` dans
// res/values*/colors.xml.
private val BrandLight = Color(0xFF6750A4)
private val BrandDark = Color(0xFFD0BCFF)
private val BackgroundLight = Color(0xFFFBF8FF)
private val BackgroundDark = Color(0xFF141218)

private val LightColors =
    lightColorScheme(
        primary = BrandLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        background = BackgroundLight,
        onBackground = Color(0xFF1D1B20),
        surface = BackgroundLight,
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EB),
        onSurfaceVariant = Color(0xFF49454E),
        outline = Color(0xFF7A757F),
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandDark,
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378A),
        onPrimaryContainer = Color(0xFFEADDFF),
        background = BackgroundDark,
        onBackground = Color(0xFFE6E0E9),
        surface = BackgroundDark,
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454E),
        onSurfaceVariant = Color(0xFFCAC4CF),
        outline = Color(0xFF948F99),
    )

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** [AppTheme] piloté par le réglage de thème de l'utilisateur (Système / Clair / Sombre). */
@Composable
fun SettingsAwareTheme(
    settings: SettingsRepository,
    content: @Composable () -> Unit,
) {
    // ThemeMode.SYSTEM (valeur initiale tant que le DataStore n'a pas livré la sienne) retombe sur
    // isSystemInDarkTheme() : jamais de flash clair/sombre incorrect pendant la lecture initiale.
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val darkTheme =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    AppTheme(darkTheme = darkTheme, content = content)
}
