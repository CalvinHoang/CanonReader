package com.canonreader.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * A lightweight HTML-to-native model. Post HTML (built by the pipeline from Standard
 * Ebooks sources) uses a small subset: p, br, i, b, h4, blockquote, ul/ol/li and hr.
 * It is parsed once into blocks that HtmlContent renders as real Compose text.
 */
sealed interface HtmlBlock {
    data class Paragraph(val text: AnnotatedString) : HtmlBlock
    data class Heading(val level: Int, val text: AnnotatedString) : HtmlBlock
    data class Quote(val blocks: List<HtmlBlock>) : HtmlBlock
    data class ListBlock(val items: List<AnnotatedString>, val ordered: Boolean) : HtmlBlock
    data object Rule : HtmlBlock
}

fun parseHtmlBlocks(html: String): List<HtmlBlock> {
    val blocks = mutableListOf<HtmlBlock>()
    Jsoup.parseBodyFragment(html).body().childNodes().forEach { parseNode(it, blocks) }
    return blocks.filterNot { it is HtmlBlock.Paragraph && it.text.text.isBlank() }
}

private val headingRegex = Regex("h[1-6]")

private fun parseNode(node: Node, blocks: MutableList<HtmlBlock>) {
    when (node) {
        is TextNode -> {
            if (node.text().isNotBlank()) {
                blocks += HtmlBlock.Paragraph(buildInline(listOf(node)))
            }
        }
        is Element -> when {
            node.tagName() == "p" -> blocks += HtmlBlock.Paragraph(buildInline(node.childNodes()))
            node.tagName().matches(headingRegex) -> blocks += HtmlBlock.Heading(
                level = node.tagName().substring(1).toInt(),
                text = buildInline(node.childNodes()),
            )
            node.tagName() == "blockquote" -> {
                val inner = mutableListOf<HtmlBlock>()
                node.childNodes().forEach { parseNode(it, inner) }
                if (inner.isNotEmpty()) blocks += HtmlBlock.Quote(inner)
            }
            node.tagName() == "ul" || node.tagName() == "ol" -> {
                val items = node.children().toList()
                    .filter { it.tagName() == "li" }
                    .map { buildInline(it.childNodes()) }
                    .filter { it.text.isNotBlank() }
                if (items.isNotEmpty()) blocks += HtmlBlock.ListBlock(items, ordered = node.tagName() == "ol")
            }
            node.tagName() == "hr" -> blocks += HtmlBlock.Rule
            else -> {
                if (node.children().any { it.isBlock }) {
                    node.childNodes().forEach { parseNode(it, blocks) }
                } else {
                    val text = buildInline(node.childNodes())
                    if (text.text.isNotBlank()) blocks += HtmlBlock.Paragraph(text)
                }
            }
        }
    }
}

private fun buildInline(nodes: List<Node>): AnnotatedString = buildAnnotatedString {
    nodes.forEach { appendInline(it) }
}

private fun AnnotatedString.Builder.appendInline(node: Node) {
    when (node) {
        // wholeText keeps the verse indentation (em spaces) that text() would collapse.
        is TextNode -> append(node.wholeText.replace(Regex("[ \\t\\r\\n]+"), " "))
        is Element -> when (node.tagName()) {
            "br" -> append('\n')
            "b", "strong" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                node.childNodes().forEach { appendInline(it) }
            }
            "i", "em", "cite" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.childNodes().forEach { appendInline(it) }
            }
            else -> node.childNodes().forEach { appendInline(it) }
        }
    }
}
