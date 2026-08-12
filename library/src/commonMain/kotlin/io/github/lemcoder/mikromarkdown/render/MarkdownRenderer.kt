package io.github.lemcoder.mikromarkdown.render

import io.github.lemcoder.mikromarkdown.model.Alignment
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.BlockQuote
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.CodeSpan
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Emphasis
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.HtmlComment
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Inline
import io.github.lemcoder.mikromarkdown.model.LineBreak
import io.github.lemcoder.mikromarkdown.model.Link
import io.github.lemcoder.mikromarkdown.model.ListBlock
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.RawBlock
import io.github.lemcoder.mikromarkdown.model.RawInline
import io.github.lemcoder.mikromarkdown.model.Strikethrough
import io.github.lemcoder.mikromarkdown.model.Strong
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import io.github.lemcoder.mikromarkdown.model.Text
import io.github.lemcoder.mikromarkdown.model.ThematicBreak

public data class MarkdownOptions(
    val bullet: Char = '-',
    val strongMarker: String = "**",
    val emphasisMarker: String = "*",
    /** Escape Markdown-significant characters in [Text] nodes. */
    val escapeText: Boolean = true,
    /** Newlines inside table cells are replaced with this, since GFM rows are single-line. */
    val tableCellLineBreak: String = "<br>",
    /** Pad table columns so the source table lines up. Off keeps output compact. */
    val padTableColumns: Boolean = false,
    /** Emit a `key: value` YAML front matter block when the document carries metadata. */
    val frontMatter: Boolean = false,
    val maxHeadingLevel: Int = 6,
    /** Drop images entirely, keeping only their alt text. */
    val imagesAsText: Boolean = false,
    /** Longest image URL kept inline; longer ones (e.g. base64 data URIs) are still emitted. */
    val maxInlineImageUrl: Int = Int.MAX_VALUE,
) {
    public companion object {
        public val Default: MarkdownOptions = MarkdownOptions()
    }
}

/** Serializes a [Document] to GitHub-Flavored Markdown. The only place Markdown syntax is produced. */
public class MarkdownRenderer(private val options: MarkdownOptions = MarkdownOptions.Default) {

    public fun render(document: Document): String {
        val body = renderBlocks(document.blocks)
        if (!options.frontMatter || document.metadata.isEmpty()) return body
        val front = document.metadata.entries.joinToString("\n") { (k, v) -> "$k: ${v.replace("\n", " ")}" }
        return "---\n$front\n---\n\n$body".trimEnd()
    }

    public fun render(blocks: List<Block>): String = renderBlocks(blocks)

    public fun renderInline(inlines: List<Inline>): String = inlines(inlines, TextContext.INLINE)

    /**
     * Blocks are written into one buffer, never re-read.
     *
     * The previous version had each block return its own string, so a nested list or quote split the whole subtree into
     * lines and re-joined it once per level of nesting — the cost of the deepest leaf multiplied by the depth above it.
     * Here a line prefix travels down the recursion and is emitted when a newline is written, so every character is
     * written exactly once.
     */
    private fun renderBlocks(blocks: List<Block>): String {
        val out = StringBuilder()
        writeBlocks(blocks, out, prefix = "")
        return out.toString().trim()
    }

    private fun writeBlocks(blocks: List<Block>, out: StringBuilder, prefix: String) {
        var wroteAny = false
        for (block in blocks) {
            val separatorStart = out.length
            if (wroteAny) {
                newLine(out, prefix)
                newLine(out, prefix)
            }
            val contentStart = out.length
            writeBlock(block, out, prefix)
            // Blocks that render to nothing must not leave their separator behind.
            if (out.length == contentStart) out.setLength(separatorStart) else wroteAny = true
        }
    }

    private fun writeBlock(block: Block, out: StringBuilder, prefix: String) {
        when (block) {
            is Heading -> {
                val text = inlines(block.content, TextContext.HEADING).collapseLines()
                if (text.isNotBlank()) {
                    out.append("#".repeat(block.level.coerceIn(1, options.maxHeadingLevel))).append(' ').append(text)
                }
            }

            is Paragraph -> appendLines(out, inlines(block.content, TextContext.BLOCK).trimEnd(), prefix)

            is CodeBlock -> {
                val fence = "`".repeat(maxOf(3, longestBacktickRun(block.code) + 1))
                out.append(fence).append(block.language.orEmpty())
                newLine(out, prefix)
                appendLines(out, block.code.trimEnd('\n'), prefix)
                newLine(out, prefix)
                out.append(fence)
            }

            is BlockQuote -> {
                out.append(QUOTE_PREFIX)
                writeBlocks(block.blocks, out, prefix + QUOTE_PREFIX)
            }

            is ListBlock -> writeList(block, out, prefix)

            is Table -> writeTable(block, out, prefix)

            ThematicBreak -> out.append("---")

            is HtmlComment -> out.append("<!-- ").append(block.text.trim()).append(" -->")

            // Already-Markdown content: only whitespace is normalized, never syntax.
            is RawBlock ->
                appendLines(
                    out,
                    block.text.replace("\r\n", "\n").replace(BLANK_LINES, "\n\n").trim(),
                    prefix,
                )
        }
    }

    private fun writeList(list: ListBlock, out: StringBuilder, prefix: String) {
        list.items.forEachIndexed { index, item ->
            if (index > 0) {
                newLine(out, prefix)
                if (list.loose) newLine(out, prefix)
            }
            val marker = if (list.ordered) "${list.start + index}. " else "${options.bullet} "
            val checkbox =
                when (item.checked) {
                    true -> "[x] "
                    false -> "[ ] "
                    null -> ""
                }
            out.append(marker).append(checkbox)
            // Continuation lines line up under the marker, not under the checkbox.
            writeBlocks(item.blocks, out, prefix + " ".repeat(marker.length))
        }
    }

    /** Appends [text], re-emitting [prefix] after each newline it contains. */
    private fun appendLines(out: StringBuilder, text: String, prefix: String) {
        for (char in text) if (char == '\n') newLine(out, prefix) else out.append(char)
    }

    /**
     * Ends the current line and opens the next one with [prefix].
     *
     * Trailing blanks go first, which is what turns a quote's "> " into ">" on an empty line and keeps list markers
     * from leaving "- " behind on an item that rendered nothing.
     */
    private fun newLine(out: StringBuilder, prefix: String) {
        while (out.isNotEmpty() && (out.last() == ' ' || out.last() == '\t')) out.setLength(out.length - 1)
        out.append('\n').append(prefix)
    }

    private fun writeTable(table: Table, out: StringBuilder, prefix: String) {
        val bodyRows = table.rows.map { expandSpans(it) }
        val headerCells = expandSpans(table.header)
        val columns = maxOf(headerCells.size, bodyRows.maxOfOrNull { it.size } ?: 0)
        if (columns == 0) return

        val header = pad(headerCells, columns)
        val rows = bodyRows.map { pad(it, columns) }
        val alignments = List(columns) { table.alignments.getOrElse(it) { Alignment.NONE } }

        val widths =
            if (options.padTableColumns) {
                List(columns) { col -> maxOf(3, header[col].length, rows.maxOfOrNull { it[col].length } ?: 0) }
            } else {
                null
            }

        out.append(row(header, widths))
        newLine(out, prefix)
        out.append(delimiterRow(alignments, widths))
        for (cells in rows) {
            newLine(out, prefix)
            out.append(row(cells, widths))
        }
        if (table.caption.isNotEmpty()) {
            newLine(out, prefix)
            newLine(out, prefix)
            out.append(options.emphasisMarker)
                .append(inlines(table.caption, TextContext.INLINE).collapseLines())
                .append(options.emphasisMarker)
        }
    }

    /** GFM has no colspan: a spanning cell keeps its text and the covered columns render empty. */
    private fun expandSpans(cells: List<TableCell>): List<String> {
        val out = ArrayList<String>(cells.size)
        for (cell in cells) {
            out += cellText(cell)
            repeat((cell.colSpan - 1).coerceAtLeast(0)) { out += "" }
        }
        return out
    }

    private fun cellText(cell: TableCell): String {
        val rendered = inlines(cell.content, TextContext.TABLE)
        // Single-line cells are the overwhelming majority; splitting them would allocate a list
        // and rejoin it to reach the same string.
        if (rendered.indexOf('\n') < 0 && rendered.indexOf('\r') < 0) return rendered.trim()
        return rendered.replace("\r\n", "\n").lines().joinToString(options.tableCellLineBreak) { it.trim() }.trim()
    }

    private fun pad(cells: List<String>, columns: Int): List<String> =
        // Rows that already match the header — every row of a well-formed table — are passed through.
        when {
            cells.size == columns -> cells
            cells.size > columns -> cells.take(columns)
            else -> cells + List(columns - cells.size) { "" }
        }

    private fun row(cells: List<String>, widths: List<Int>?): String =
        cells
            .mapIndexed { index, cell -> if (widths == null) cell else cell.padEnd(widths[index]) }
            .joinToString(" | ", "| ", " |")

    private fun delimiterRow(alignments: List<Alignment>, widths: List<Int>?): String =
        alignments
            .mapIndexed { index, alignment ->
                val width = widths?.get(index) ?: 3
                when (alignment) {
                    Alignment.NONE -> "-".repeat(width)
                    Alignment.LEFT -> ":" + "-".repeat(width - 1)
                    Alignment.RIGHT -> "-".repeat(width - 1) + ":"
                    Alignment.CENTER -> ":" + "-".repeat(width - 2) + ":"
                }
            }
            .joinToString(" | ", "| ", " |")

    private fun inlines(inlines: List<Inline>, context: TextContext): String {
        val sb = StringBuilder()
        for (inline in inlines) sb.appendInline(inline, context)
        return sb.toString()
    }

    private fun StringBuilder.appendInline(inline: Inline, context: TextContext) {
        when (inline) {
            is Text -> append(escape(inline.value, context, atLineStart = isAtLineStart(context)))

            is Strong -> wrap(inline.content, options.strongMarker, context)
            is Emphasis -> wrap(inline.content, options.emphasisMarker, context)
            is Strikethrough -> wrap(inline.content, "~~", context)

            is CodeSpan -> {
                val ticks = "`".repeat(longestBacktickRun(inline.code) + 1)
                val padding = if (inline.code.startsWith('`') || inline.code.endsWith('`')) " " else ""
                append(ticks).append(padding).append(inline.code.replace("\n", " ")).append(padding).append(ticks)
            }

            is Link -> {
                val label = inlines(inline.content, TextContext.INLINE).collapseLines().ifBlank { inline.url }
                append('[').append(label).append("](").append(encodeUrl(inline.url))
                inline.title?.let { append(" \"").append(it.replace("\"", "\\\"")).append('"') }
                append(')')
            }

            is Image -> {
                val alt = inline.alt.replace("\n", " ").replace("]", "\\]")
                if (options.imagesAsText || inline.url.isBlank()) {
                    if (alt.isNotBlank()) append(alt)
                } else if (inline.url.length > options.maxInlineImageUrl) {
                    if (alt.isNotBlank()) append(alt)
                } else {
                    append("![").append(alt).append("](").append(encodeUrl(inline.url))
                    inline.title?.let { append(" \"").append(it.replace("\"", "\\\"")).append('"') }
                    append(')')
                }
            }

            LineBreak -> if (context == TextContext.TABLE) append(' ') else append("\\\n")

            is RawInline -> append(inline.text)
        }
    }

    private fun StringBuilder.wrap(content: List<Inline>, marker: String, context: TextContext) {
        val inner = inlines(content, context)
        if (inner.isBlank()) {
            append(inner)
            return
        }
        // Markers must hug the text: "**bold** " not "** bold **".
        val leading = inner.takeWhile { it.isWhitespace() }
        val trailing = inner.takeLastWhile { it.isWhitespace() }
        append(leading).append(marker).append(inner.trim()).append(marker).append(trailing)
    }

    private fun StringBuilder.isAtLineStart(context: TextContext): Boolean =
        context != TextContext.TABLE && (isEmpty() || last() == '\n')

    private fun escape(value: String, context: TextContext, atLineStart: Boolean): String {
        // Most text needs neither newline normalization nor escaping. Returning it untouched keeps
        // the common cell — a word or a number — from allocating anything at all.
        val text = if (value.indexOf('\r') < 0) value else value.replace("\r\n", "\n").replace('\r', '\n')

        if (!options.escapeText) {
            return if (context == TextContext.TABLE && text.indexOf('|') >= 0) text.replace("|", "\\|") else text
        }

        val first = firstEscapeIndex(text, context, atLineStart)
        if (first < 0) return text

        return buildString(text.length + ESCAPE_HEADROOM) {
            append(text, 0, first)
            for (index in first until text.length) {
                if (needsEscape(text, index, context, atLineStart)) append('\\')
                append(text[index])
            }
        }
    }

    private fun firstEscapeIndex(text: String, context: TextContext, atLineStart: Boolean): Int {
        for (index in text.indices) if (needsEscape(text, index, context, atLineStart)) return index
        return -1
    }

    private fun needsEscape(text: String, index: Int, context: TextContext, atLineStart: Boolean): Boolean =
        when (val ch = text[index]) {
            '\\',
            '*',
            '`',
            '[',
            ']' -> true
            '|' -> context == TextContext.TABLE
            // Intraword underscores (snake_case) are not emphasis in CommonMark.
            '_' -> !isIntraword(text, index)
            '<' -> text.getOrNull(index + 1)?.let { it.isLetter() || it == '/' || it == '!' } == true
            '&' -> looksLikeEntity(text, index)
            // The rest only introduce block syntax at the start of a line.
            '#',
            '>',
            '=' -> startsLine(text, index, atLineStart)
            '-',
            '+' -> startsLine(text, index, atLineStart) && text.getOrNull(index + 1)?.isWhitespace() != false
            // "1. item" in running text would become a list; escape the delimiter, not the digits.
            '.',
            ')' -> followsOrderedListMarker(text, index, atLineStart)
            else -> false
        }

    private fun isIntraword(text: String, index: Int): Boolean =
        text.getOrNull(index - 1)?.isLetterOrDigit() == true && text.getOrNull(index + 1)?.isLetterOrDigit() == true

    /** True when only indentation separates [index] from the start of its line. */
    private fun startsLine(text: String, index: Int, atLineStart: Boolean): Boolean {
        var i = index - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        return if (i < 0) atLineStart else text[i] == '\n'
    }

    private fun followsOrderedListMarker(text: String, index: Int, atLineStart: Boolean): Boolean {
        var digits = index - 1
        while (digits >= 0 && text[digits].isDigit()) digits--
        if (digits == index - 1) return false
        return startsLine(text, digits + 1, atLineStart) && startsOrderedList(text, index - 1)
    }

    private fun startsOrderedList(text: String, digitIndex: Int): Boolean {
        var start = digitIndex
        while (start > 0 && text[start - 1].isDigit()) start--
        if (start > 0 && !text[start - 1].isWhitespace()) return false
        var end = digitIndex
        while (end + 1 < text.length && text[end + 1].isDigit()) end++
        val delimiter = text.getOrNull(end + 1) ?: return false
        if (delimiter != '.' && delimiter != ')') return false
        val after = text.getOrNull(end + 2)
        return after == null || after == ' ' || after == '\n'
    }

    /**
     * Scans at most [MAX_ENTITY_LENGTH] characters ahead.
     *
     * Searching the whole string for the next semicolon made this quadratic on text holding many ampersands and few
     * semicolons — query strings, for one.
     */
    private fun looksLikeEntity(text: String, index: Int): Boolean {
        val limit = minOf(text.length, index + 1 + MAX_ENTITY_LENGTH)
        for (position in index + 1 until limit) {
            val char = text[position]
            if (char == ';') return position > index + 1
            if (!char.isLetterOrDigit() && char != '#') return false
        }
        return false
    }

    private fun encodeUrl(url: String): String = url.replace(" ", "%20").replace("(", "%28").replace(")", "%29")

    private fun longestBacktickRun(text: String): Int {
        var longest = 0
        var current = 0
        for (ch in text) {
            if (ch == '`') {
                current++
                if (current > longest) longest = current
            } else {
                current = 0
            }
        }
        return longest
    }

    private fun String.collapseLines(): String = replace("\r\n", "\n").lines().joinToString(" ") { it.trim() }.trim()

    private enum class TextContext {
        BLOCK,
        INLINE,
        HEADING,
        TABLE,
    }

    public companion object {
        public val Default: MarkdownRenderer = MarkdownRenderer()

        private val BLANK_LINES = Regex("\n{3,}")

        /** Room for a few backslashes before the builder has to grow. */
        private const val ESCAPE_HEADROOM = 8

        /** Longest entity name worth looking for, e.g. `&thetasym;`. */
        private const val MAX_ENTITY_LENGTH = 10

        private const val QUOTE_PREFIX = "> "
    }
}
