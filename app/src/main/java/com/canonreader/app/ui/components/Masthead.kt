package com.canonreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canonreader.app.ui.theme.CanonGilt
import com.canonreader.app.ui.theme.CanonOnRibbon
import com.canonreader.app.ui.theme.Cinzel
import com.canonreader.app.ui.theme.ribbonColor

/** Compact masthead for the Feed: "The CANON", cut in Roman capitals like a title page. */
@Composable
fun CanonMasthead(
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopRibbon(modifier = modifier, actions = actions) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(
                modifier = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "The ",
                    fontFamily = Cinzel,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 1.sp,
                    color = CanonGilt,
                )
                Text(
                    "CANON",
                    fontFamily = Cinzel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 3.sp,
                    color = CanonOnRibbon,
                )
            }
        }
    }
}

/** The brand ribbon: an oxblood bar edged below by a gilt double rule. */
@Composable
fun TopRibbon(
    modifier: Modifier = Modifier,
    containerColor: Color = ribbonColor(),
    contentColor: Color = CanonOnRibbon,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
                actions()
            }
        }
        GiltRule()
    }
}

/** A thick-and-thin gilt fillet, as under a printed running head. */
@Composable
fun GiltRule(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(ribbonColor())) {
        Spacer(Modifier.fillMaxWidth().height(3.dp).background(CanonGilt))
        Spacer(Modifier.fillMaxWidth().height(2.dp))
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(CanonGilt))
    }
}

/** A plain titled ribbon for secondary screens, with an optional back button slot. */
@Composable
fun TitleRibbon(
    title: String,
    modifier: Modifier = Modifier,
    navigation: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopRibbon(modifier = modifier, actions = actions) {
        navigation()
        Text(
            title,
            fontFamily = Cinzel,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 1.sp,
            maxLines = 1,
            color = CanonOnRibbon,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}
