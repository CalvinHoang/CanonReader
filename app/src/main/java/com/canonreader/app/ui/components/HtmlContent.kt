package com.canonreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canonreader.app.ui.theme.CanonGoldSoft
import com.canonreader.app.ui.theme.Merriweather
import com.canonreader.app.ui.theme.RobotoFlex

/** Renders post HTML as native Compose text in the blog's reading type. */
@Composable
fun HtmlContent(
    html: String,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = Merriweather,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 27.sp,
    selectable: Boolean = false,
) {
    val blocks = remember(html) { parseHtmlBlocks(html) }
    val bodyStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            blocks.forEach { block -> RenderBlock(block, bodyStyle) }
        }
    }
    if (selectable) {
        SelectableSurface(modifier = modifier) { content() }
    } else {
        Box(modifier = modifier) { content() }
    }
}

@Composable
private fun RenderBlock(block: HtmlBlock, bodyStyle: TextStyle) {
    when (block) {
        is HtmlBlock.Paragraph -> Text(text = block.text, style = bodyStyle)
        is HtmlBlock.Heading -> Text(
            text = block.text,
            style = bodyStyle.copy(
                fontFamily = RobotoFlex,
                fontWeight = FontWeight.Bold,
                fontSize = when (block.level) {
                    1 -> 24.sp
                    2 -> 22.sp
                    3 -> 20.sp
                    4 -> 17.sp
                    else -> 16.sp
                },
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        is HtmlBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(CanonGoldSoft)
            )
            Column(
                Modifier.padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                block.blocks.forEach { RenderBlock(it, bodyStyle) }
            }
        }
        is HtmlBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = if (block.ordered) "${index + 1}." else "•",
                        style = bodyStyle,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(text = item, style = bodyStyle)
                }
            }
        }
        HtmlBlock.Rule -> HorizontalDivider()
    }
}
