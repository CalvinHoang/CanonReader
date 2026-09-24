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
import com.canonreader.app.ui.theme.CanonGold
import com.canonreader.app.ui.theme.CanonInk
import com.canonreader.app.ui.theme.RobotoFlex

/** Compact masthead for the Feed: "THE CANON", set like a blog wordmark. */
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
            ) {
                Text(
                    "THE ",
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.Normal,
                    fontSize = 21.sp,
                    lineHeight = 22.sp,
                    color = CanonInk,
                )
                Text(
                    "CANON",
                    fontFamily = RobotoFlex,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    lineHeight = 22.sp,
                    color = CanonInk,
                )
            }
        }
    }
}

/** The brand ribbon: ochre bar over a thin black rule. */
@Composable
fun TopRibbon(
    modifier: Modifier = Modifier,
    containerColor: Color = CanonGold,
    contentColor: Color = CanonInk,
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
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
                actions()
            }
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(Color.Black),
        )
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
            fontFamily = RobotoFlex,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 19.sp,
            maxLines = 1,
            color = CanonInk,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
    }
}
