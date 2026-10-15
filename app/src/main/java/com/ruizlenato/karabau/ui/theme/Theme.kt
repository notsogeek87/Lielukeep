package com.ruizlenato.karabau.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.LocalAutofillHighlightBrush
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.R
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Titres un cran plus affirmés que la valeur par défaut : la hiérarchie titre / métadonnées se lit d'un coup d'œil. */
private val BaseTypography = Typography()
val KarabauTypography = BaseTypography.copy(
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private fun brandColorScheme(context: Context, dark: Boolean): ColorScheme {
    fun c(id: Int) = Color(context.getColor(id))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(R.color.brand_primary),
        onPrimary = c(R.color.brand_on_primary),
        primaryContainer = c(R.color.brand_primary_container),
        onPrimaryContainer = c(R.color.brand_on_primary_container),
        secondary = c(R.color.brand_secondary),
        onSecondary = c(R.color.brand_on_secondary),
        secondaryContainer = c(R.color.brand_secondary_container),
        onSecondaryContainer = c(R.color.brand_on_secondary_container),
        tertiary = c(R.color.brand_tertiary),
        onTertiary = c(R.color.brand_on_tertiary),
        tertiaryContainer = c(R.color.brand_tertiary_container),
        onTertiaryContainer = c(R.color.brand_on_tertiary_container),
        background = c(R.color.brand_background),
        onBackground = c(R.color.brand_on_background),
        surface = c(R.color.brand_surface),
        onSurface = c(R.color.brand_on_surface),
        surfaceVariant = c(R.color.brand_surface_variant),
        onSurfaceVariant = c(R.color.brand_on_surface_variant),
        outline = c(R.color.brand_outline),
        outlineVariant = c(R.color.brand_outline_variant),
        surfaceContainerLow = c(R.color.brand_surface_container_low),
        surfaceContainer = c(R.color.brand_surface_container),
        surfaceContainerHigh = c(R.color.brand_surface_container_high),
        surfaceContainerHighest = c(R.color.brand_surface_container_highest),
        error = c(R.color.brand_error),
        onError = c(R.color.brand_on_error),
        errorContainer = c(R.color.brand_error_container),
        onErrorContainer = c(R.color.brand_on_error_container)
    )
}

@Composable
fun KarabauTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Material You quand le téléphone le permet (Android 12+) ; sinon la palette LieluKeep (res/values*/colors.xml).
    val dynamicAvailable = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable -> dynamicLightColorScheme(context)
        else -> brandColorScheme(context, darkTheme)
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalAutofillHighlightBrush provides SolidColor(Color.Transparent)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ExpressiveShapes,
            typography = KarabauTypography,
            content = content
        )
    }
}
