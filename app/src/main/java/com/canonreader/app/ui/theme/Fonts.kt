package com.canonreader.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.canonreader.app.R

// Two families for a printed-book feel:
// Cinzel (Roman inscriptional capitals) for the masthead and ribbon titles,
// EB Garamond (a revival of Claude Garamont's 16th-century type) for everything else.
// Both are static instances cut from the Google Fonts variable files (SIL OFL).

val Cinzel = FontFamily(
    Font(R.font.cinzel_regular, FontWeight.Normal),
    Font(R.font.cinzel_bold, FontWeight.Bold),
)

val Garamond = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.eb_garamond_semibold, FontWeight.SemiBold),
    Font(R.font.eb_garamond_bold, FontWeight.Bold),
)
