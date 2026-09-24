package com.canonreader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

// Marginal Reader's layout with its own colours: an ochre masthead instead of MR green,
// so the two apps are easy to tell apart side by side.
val CanonGold = Color(0xFFE3B23C)      // masthead / brand
val CanonGoldSoft = Color(0xFFD9A441)  // blockquote border, accents
val CanonGoldDark = Color(0xFF5A4205)
val CanonLinkBlue = Color(0xFF1F3F8F)
val CanonLinkBlueDark = Color(0xFF9DB2EE)
val CanonPaper = Color(0xFFFFFFFF)
val CanonInk = Color(0xFF111111)
val CanonGray = Color(0xFF6F6A60)
val CanonBorder = Color(0xFFD2CCC0)

private val LightColors = lightColorScheme(
    primary = CanonGoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7E8C2),
    onPrimaryContainer = CanonGoldDark,
    secondary = Color(0xFF8A6512),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4ECD8),
    onSecondaryContainer = CanonGoldDark,
    tertiary = CanonLinkBlue,
    background = CanonPaper,
    onBackground = CanonInk,
    surface = CanonPaper,
    onSurface = CanonInk,
    surfaceVariant = Color(0xFFF4F1EA),
    onSurfaceVariant = CanonGray,
    outline = CanonBorder,
    outlineVariant = Color(0xFFE9E4DA),
)

private val DarkColors = darkColorScheme(
    primary = CanonGold,
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF4A3708),
    onPrimaryContainer = Color(0xFFF7E3AE),
    secondary = CanonGoldSoft,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF332812),
    onSecondaryContainer = Color(0xFFF7E3AE),
    tertiary = CanonLinkBlueDark,
    background = Color(0xFF131211),
    onBackground = Color(0xFFE9E5DC),
    surface = Color(0xFF131211),
    onSurface = Color(0xFFE9E5DC),
    surfaceVariant = Color(0xFF1F1D1A),
    onSurfaceVariant = Color(0xFFB0AA9E),
    outline = Color(0xFF45413A),
    outlineVariant = Color(0xFF2D2A26),
)

// Headings in Roboto Flex, everything else in Open Sans; Merriweather is applied
// directly by HtmlContent for post body text.
private val CanonTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = RobotoFlex),
        headlineLarge = base.headlineLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = OpenSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = OpenSans),
        bodySmall = base.bodySmall.copy(fontFamily = OpenSans),
        labelLarge = base.labelLarge.copy(fontFamily = OpenSans),
        labelMedium = base.labelMedium.copy(fontFamily = OpenSans),
        labelSmall = base.labelSmall.copy(fontFamily = OpenSans),
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
