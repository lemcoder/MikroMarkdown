package io.github.lemcoder.mikromarkdown.utils

import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.BlockQuote
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.CodeSpan
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Emphasis
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Inline
import io.github.lemcoder.mikromarkdown.model.LineBreak
import io.github.lemcoder.mikromarkdown.model.Link
import io.github.lemcoder.mikromarkdown.model.ListBlock
import io.github.lemcoder.mikromarkdown.model.ListItem
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.Strikethrough
import io.github.lemcoder.mikromarkdown.model.Strong
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import io.github.lemcoder.mikromarkdown.model.Text
import io.github.lemcoder.mikromarkdown.model.ThematicBreak
import io.github.lemcoder.mikromarkdown.model.plainText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Walks an HTML DOM into the shared document model.
 *
 * Replaces the previous HTML → Markdown string conversion: tables, lists and inline emphasis become model nodes, so the
 * Markdown renderer owns all syntax decisions.
 */
object HtmlToDocument {

    private val DROPPED_TAGS =
        setOf(
            "script",
            "style",
            "noscript",
            "template",
            "button",
            "svg",
            "iframe",
            "form",
            "input",
            "select",
        )

    private val HEADINGS = mapOf("h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)

    /** Tags that only group other content; their children are lifted into the parent block flow. */
    private val CONTAINERS =
        setOf(
            "div",
            "section",
            "article",
            "main",
            "header",
            "footer",
            "aside",
            "nav",
            "body",
            "html",
            "figure",
            "details",
            "summary",
            "fieldset",
            "center",
            "hgroup",
            "picture",
            "colgroup",
        )

    fun parse(html: String, baseUri: String = ""): Document {
        val doc = Jsoup.parse(html, baseUri)
        doc.select(DROPPED_TAGS.joinToString(", ")).remove()
        val title = doc.title().ifBlank { null }
        val root = doc.body() ?: doc
        val blocks = blocks(root)
        return Document(blocks = blocks, title = title ?: blocks.headingTitle())
    }

    private fun List<Block>.headingTitle(): String? =
        (firstOrNull { it is Heading } as? Heading)?.content?.plainText()?.trim()?.ifBlank { null }

    /** Converts an element's children into blocks, flushing runs of inline content into paragraphs. */
    private fun blocks(parent: Element): List<Block> {
        val out = mutableListOf<Block>()
        val pending = mutableListOf<Inline>()

        fun flush() {
            val trimmed = pending.trimEdges()
            if (trimmed.isNotEmpty()) out += Paragraph(trimmed)
            pending.clear()
        }

        for (node in parent.childNodes()) {
            when {
                node is TextNode -> {
                    val text = node.normalizedText()
                    if (text.isNotEmpty()) pending += Text(text)
                }

                node is Element && node.isBlockLevel() -> {
                    flush()
                    out += blockFor(node)
                }

                node is Element && node.tagName() == "br" -> pending += LineBreak

                // Inline elements (links, emphasis, …) keep their wrapper — appendNode, not children.
                node is Element -> pending.appendNode(node)
            }
        }
        flush()
        return out
    }

    private fun Element.isBlockLevel(): Boolean =
        when (tagName()) {
            in HEADINGS,
            "p",
            "ul",
            "ol",
            "table",
            "pre",
            "blockquote",
            "hr",
            "dl",
            "li",
            "figcaption" -> true
            in CONTAINERS -> true
            else -> false
        }

    private fun blockFor(element: Element): List<Block> =
        when (val tag = element.tagName()) {
            in HEADINGS ->
                listOfNotNull(
                    inlines(element)
                        .trimEdges()
                        .takeIf { it.isNotEmpty() }
                        ?.let { Heading(HEADINGS.getValue(tag), it, element.id().ifBlank { null }) }
                )

            "p" -> listOfNotNull(inlines(element).trimEdges().takeIf { it.isNotEmpty() }?.let { Paragraph(it) })
            "ul",
            "ol" -> listBlock(element)
            "table" -> table(element)
            "pre" -> listOf(codeBlock(element))
            "blockquote" -> blocks(element).takeIf { it.isNotEmpty() }?.let { listOf(BlockQuote(it)) } ?: emptyList()
            "hr" -> listOf(ThematicBreak)
            "dl" -> definitionList(element)
            "figcaption" ->
                listOfNotNull(
                    inlines(element).trimEdges().takeIf { it.isNotEmpty() }?.let { Paragraph(listOf(Emphasis(it))) }
                )
            // Containers and stray <li> outside a list contribute their children directly.
            else -> blocks(element)
        }

    private fun listBlock(element: Element): List<Block> {
        val ordered = element.tagName() == "ol"
        val start = element.attr("start").toIntOrNull() ?: 1
        val items =
            element
                .children()
                .filter { it.tagName() == "li" }
                .map { li -> ListItem(blocks = blocks(li)) }
                .filter { it.blocks.isNotEmpty() }
        if (items.isEmpty()) return emptyList()
        return listOf(ListBlock(ordered = ordered, items = items, start = start))
    }

    private fun codeBlock(element: Element): Block {
        val code = element.selectFirst("code") ?: element
        val language =
            code.classNames().firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }?.substringAfter('-')
        return CodeBlock(code.wholeText().trimEnd(), language)
    }

    private fun definitionList(element: Element): List<Block> {
        val out = mutableListOf<Block>()
        for (child in element.children()) {
            val content = inlines(child).trimEdges()
            if (content.isEmpty()) continue
            when (child.tagName()) {
                "dt" -> out += Paragraph(listOf(Strong(content)))
                "dd" -> out += Paragraph(content)
            }
        }
        return out
    }

    private fun table(element: Element): List<Block> {
        val caption = element.selectFirst("caption")?.let { inlines(it).trimEdges() } ?: emptyList()
        val rows = element.select("tr").filter { it.parentTable() === element }

        var header: List<TableCell> = emptyList()
        val body = mutableListOf<List<TableCell>>()

        for ((index, tr) in rows.withIndex()) {
            val cells =
                tr.children()
                    .filter { it.tagName() == "th" || it.tagName() == "td" }
                    .map { cell ->
                        TableCell(
                            content = inlines(cell).trimEdges(),
                            colSpan = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 100) ?: 1,
                            rowSpan = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 100) ?: 1,
                        )
                    }
            if (cells.isEmpty()) continue

            val isHeaderRow =
                index == 0 &&
                    tr.children().all { it.tagName() == "th" } &&
                    (tr.parent()?.tagName() == "thead" || header.isEmpty())

            if (isHeaderRow && header.isEmpty()) header = cells else body += cells
        }

        if (header.isEmpty() && body.isEmpty()) return emptyList()
        return listOf(Table(header = header, rows = body, caption = caption))
    }

    /** The nearest enclosing table, so nested tables do not steal each other's rows. */
    private fun Element.parentTable(): Element? = parents().firstOrNull { it.tagName() == "table" }

    private fun inlines(element: Element): List<Inline> {
        val out = mutableListOf<Inline>()
        for (node in element.childNodes()) out.appendNode(node)
        return out
    }

    private fun MutableList<Inline>.appendNode(node: Node) {
        when (node) {
            is TextNode -> {
                val text = node.normalizedText()
                if (text.isNotEmpty()) add(Text(text))
            }

            is Element ->
                when (node.tagName()) {
                    "br" -> add(LineBreak)
                    "img" -> {
                        val src = node.attr("abs:src").ifBlank { node.attr("src") }
                        val alt = node.attr("alt")
                        if (src.isNotBlank()) add(Image(alt, src, node.attr("title").ifBlank { null }))
                    }

                    "a" -> {
                        val href = node.attr("abs:href").ifBlank { node.attr("href") }
                        val content = inlines(node).trimEdges()
                        when {
                            content.isEmpty() -> Unit
                            href.isBlank() || href.startsWith("javascript:") -> addAll(content)
                            else -> add(Link(content, href, node.attr("title").ifBlank { null }))
                        }
                    }

                    "strong",
                    "b" -> wrapped(node) { Strong(it) }
                    "em",
                    "i",
                    "cite",
                    "var" -> wrapped(node) { Emphasis(it) }
                    "del",
                    "s",
                    "strike" -> wrapped(node) { Strikethrough(it) }
                    "code",
                    "kbd",
                    "samp",
                    "tt" -> {
                        val code = node.wholeText().trim()
                        if (code.isNotEmpty()) add(CodeSpan(code))
                    }

                    // Block-level content encountered inline (e.g. a <div> inside a <td>): keep its text.
                    else -> addAll(inlines(node))
                }
        }
    }

    private inline fun MutableList<Inline>.wrapped(node: Element, wrap: (List<Inline>) -> Inline) {
        val content = inlines(node)
        val trimmed = content.trimEdges()
        if (trimmed.isEmpty()) return
        if (content.startsWithSpace()) add(Text(" "))
        add(wrap(trimmed))
        if (content.endsWithSpace()) add(Text(" "))
    }

    /** HTML collapses runs of whitespace; do the same before the text reaches the model. */
    // Non-breaking spaces are not collapsible whitespace in HTML, so they survive verbatim.
    private fun TextNode.normalizedText(): String = wholeText.replace(Regex("\\s+"), " ")

    private fun List<Inline>.startsWithSpace(): Boolean = (firstOrNull() as? Text)?.value?.startsWith(" ") == true

    private fun List<Inline>.endsWithSpace(): Boolean = (lastOrNull() as? Text)?.value?.endsWith(" ") == true

    /** Drops leading/trailing whitespace-only text so emphasis markers hug their content. */
    private fun List<Inline>.trimEdges(): List<Inline> {
        var start = 0
        var end = size
        while (start < end && this[start].isBlankText()) start++
        while (end > start && this[end - 1].isBlankText()) end--
        if (start >= end) return emptyList()
        val slice = subList(start, end).toMutableList()
        (slice.first() as? Text)?.let { slice[0] = Text(it.value.trimStart()) }
        (slice.last() as? Text)?.let { slice[slice.lastIndex] = Text(it.value.trimEnd()) }
        return slice.filter { !(it is Text && it.value.isEmpty()) }
    }

    // Kotlin's trim()/isBlank() drop Unicode spacing (thin, hair, …) but keep NBSP, which is content.
    private fun Inline.isBlankText(): Boolean = this is Text && value.isBlank()
}
