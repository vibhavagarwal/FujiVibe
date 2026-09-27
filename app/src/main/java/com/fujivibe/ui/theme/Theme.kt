package com.fujivibe.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fujivibe.R

/** The one accent: a film-canister amber for everything that means "on" or "do this". */
val Amber = Color(0xFFF2B33D)

/** Text on an amber fill. */
val OnAmber = Color(0xFF1C1405)

/** Near-black backdrop, a touch warmer than pure black. */
val Ink = Color(0xFF0C0C0C)

/** Off-white text; softer on the eyes than pure white over a dark screen. */
val Paper = Color(0xFFF2F2F2)

/** Secondary text and quiet controls. */
val PaperMuted = Paper.copy(alpha = 0.6f)

/** Translucent backing for controls drawn over the live preview. */
val Scrim = Color.Black.copy(alpha = 0.55f)

/** Barlow Semi Condensed: narrow like a camera's display, so readouts fit comfortably. */
val Barlow = FontFamily(
    Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
)

private val ColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    secondary = Amber,
    onSecondary = OnAmber,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = PaperMuted,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
)

/** Every text style in Barlow, with even-width digits so changing numbers don't jiggle. */
private val AppTypography: Typography = Typography().let { base ->
    fun TextStyle.inBarlow() = copy(fontFamily = Barlow, fontFeatureSettings = "tnum")
    base.copy(
        displayLarge = base.displayLarge.inBarlow(),
        displayMedium = base.displayMedium.inBarlow(),
        displaySmall = base.displaySmall.inBarlow(),
        headlineLarge = base.headlineLarge.inBarlow(),
        headlineMedium = base.headlineMedium.inBarlow(),
        headlineSmall = base.headlineSmall.inBarlow(),
        titleLarge = base.titleLarge.inBarlow(),
        titleMedium = base.titleMedium.inBarlow(),
        titleSmall = base.titleSmall.inBarlow(),
        bodyLarge = base.bodyLarge.inBarlow(),
        bodyMedium = base.bodyMedium.inBarlow(),
        bodySmall = base.bodySmall.inBarlow(),
        labelLarge = base.labelLarge.inBarlow(),
        labelMedium = base.labelMedium.inBarlow(),
        labelSmall = base.labelSmall.inBarlow(),
    )
}

@Composable
fun FujiVibeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography) {
        // Text outside a Surface would otherwise default to black, invisible on the dark app.
        CompositionLocalProvider(LocalContentColor provides Paper, content = content)
    }
}
