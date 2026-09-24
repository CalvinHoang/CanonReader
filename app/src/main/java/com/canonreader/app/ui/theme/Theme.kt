package com.canonreader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A Renaissance printed-book palette: iron-gall ink on vellum, an oxblood ribbon
// like a rubricated heading, gilt rules, and lapis-blue links.
val CanonOxblood = Color(0xFF7A2418)      // masthead / brand
val CanonOxbloodDeep = Color(0xFF4E1911)  // masthead in dark mode
val CanonGilt = Color(0xFFB8913F)         // rules under the ribbon, blockquote border
val CanonGiltPale = Color(0xFFE9D6A8)
val CanonVellum = Color(0xFFF5EDDC)       // page
val CanonInk = Color(0xFF2B2118)          // text
val CanonSepia = Color(0xFF6B5A48)        // secondary text
val CanonLapis = Color(0xFF22427A)
val CanonLapisDark = Color(0xFF9DB4E0)
val CanonBorder = Color(0xFFD6C7A6)

/** Text and icons drawn on the oxblood ribbon. */
val CanonOnRibbon = Color(0xFFF5EDDC)

private val LightColors = lightColorScheme(
    primary = CanonOxblood,
    onPrimary = CanonVellum,
    primaryContainer = Color(0xFFF1DCCF),
    onPrimaryContainer = Color(0xFF3F0F08),
    secondary = Color(0xFF7C6127),
    onSecondary = CanonVellum,
    secondaryContainer = CanonGiltPale,
    onSecondaryContainer = Color(0xFF3B2C0A),
    tertiary = CanonLapis,
    background = CanonVellum,
    onBackground = CanonInk,
    surface = CanonVellum,
    onSurface = CanonInk,
    surfaceVariant = Color(0xFFECE1C9),
    onSurfaceVariant = CanonSepia,
    surfaceContainerLowest = Color(0xFFFAF4E8),
    surfaceContainerLow = Color(0xFFF1E8D4),
    surfaceContainer = Color(0xFFEDE2CA),
    surfaceContainerHigh = Color(0xFFE8DCC1),
    surfaceContainerHighest = Color(0xFFE2D5B8),
    outline = CanonBorder,
    outlineVariant = Color(0xFFE4D8BD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0B7A6),
    onPrimary = Color(0xFF4A140C),
    primaryContainer = CanonOxbloodDeep,
    onPrimaryContainer = Color(0xFFF7DDD2),
    secondary = Color(0xFFD9B66A),
    onSecondary = Color(0xFF3B2C0A),
    secondaryContainer = Color(0xFF4A3A18),
    onSecondaryContainer = Color(0xFFF3E1B5),
    tertiary = CanonLapisDark,
    background = Color(0xFF17130F),
    onBackground = Color(0xFFE9DEC6),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFE9DEC6),
    surfaceVariant = Color(0xFF241E18),
    onSurfaceVariant = Color(0xFFB3A48C),
    surfaceContainerLowest = Color(0xFF120F0C),
    surfaceContainerLow = Color(0xFF1C1713),
    surfaceContainer = Color(0xFF211B16),
    surfaceContainerHigh = Color(0xFF2A231C),
    surfaceContainerHighest = Color(0xFF342B23),
    outline = Color(0xFF4F4436),
    outlineVariant = Color(0xFF30281F),
)

// EB Garamond throughout, a size up from Material's defaults since Garamond
// sets small on the body. Cinzel is applied directly by the masthead and ribbons.
private val CanonTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = Garamond),
        headlineLarge = base.headlineLarge.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 33.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
        titleSmall = base.titleSmall.copy(fontFamily = Garamond, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = Garamond, fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Garamond, fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = base.bodySmall.copy(fontFamily = Garamond, fontSize = 14.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Garamond, fontSize = 16.sp),
        labelMedium = base.labelMedium.copy(fontFamily = Garamond, fontSize = 14.sp),
        labelSmall = base.labelSmall.copy(fontFamily = Garamond, fontSize = 13.sp, letterSpacing = 0.3.sp),
    )
}

@Composable
fun CanonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CanonTypography,
        content = content,
    )
}

/** The ribbon's oxblood, deepened in dark mode so it doesn't glare against the walnut page. */
@Composable
@ReadOnlyComposable
fun ribbonColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) CanonOxbloodDeep else CanonOxblood
