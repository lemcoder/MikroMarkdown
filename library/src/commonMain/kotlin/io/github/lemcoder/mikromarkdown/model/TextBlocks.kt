package io.github.lemcoder.mikromarkdown.model

/**
 * Turns extracted plain text into paragraph blocks.
 *
 * Public because it is what any text-extracting converter needs, including ones outside this module: the `:pdfium`
 * module builds its documents with it.
 *
 * Blank lines separate paragraphs; soft-wrapped lines inside a paragraph are rejoined, so the Markdown does not inherit
 * the source layout's line breaks.
 */
public fun plainTextBlocks(text: String, reflow: Boolean = true): List<Block> {
    val normalized =
        text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            // Form feeds mark PDF page breaks; a paragraph boundary is a blank line, which takes two newlines.
            .replace(FORM_FEED, "\n\n")
    val paragraphs = mutableListOf<String>()

    for (chunk in normalized.split(PARAGRAPH_BREAK)) {
        val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) continue
        val joined = if (reflow) joinWrappedLines(lines) else lines.joinToString("\n")
        // A paragraph ending mid-word means the break was a layout artifact, not a real one.
        val previous = paragraphs.lastOrNull()
        if (reflow && previous != null && previous.endsWithWordBreak()) {
            paragraphs[paragraphs.lastIndex] = joinWrappedLines(listOf(previous, joined))
        } else {
            paragraphs += joined
        }
    }

    return paragraphs.map { Paragraph(it) }
}

private val PARAGRAPH_BREAK = Regex("\n[ \t]*\n")

private const val FORM_FEED = "\u000C"

/**
 * Rejoins soft-wrapped lines.
 *
 * A line ending mid-word continues straight into the next; any other line break was the space the wrap replaced.
 *
 * The hyphen stays either way, and nothing here tries to work out which kind it was. Geometry cannot say —
 * `FPDFText_GetCharBox` settles only that the line ended at the hyphen, which is as true of `chat-` `optimized` as of
 * `con-` `firming`. Weighing it against the rest of the document was tried and cost more than it returned: a compound
 * whose halves appear nowhere else, `chat-optimized` among them, came out fused as `chatoptimized`, a word that is in
 * no document anywhere. Keeping the hyphen writes `con-firming` where the page meant `confirming`, which reads a little
 * worse and destroys nothing — the join is still there for a reader, and for anything downstream that knows more than
 * we do.
 */
private fun joinWrappedLines(lines: List<String>): String {
    val sb = StringBuilder()
    for ((index, line) in lines.withIndex()) {
        when {
            index == 0 -> sb.append(line)
            sb.endsWithWordBreak() && line.first().isLetter() -> sb.append(line)
            else -> sb.append(' ').append(line)
        }
    }
    return sb.toString()
}

/** A trailing hyphen with a letter before it: the mark of a word the layout cut in half. */
private fun CharSequence.endsWithWordBreak(): Boolean =
    length > 1 && this[length - 1].isHyphen() && this[length - 2].isLetter()

/** The hyphens a line break can end on; a document may write any of them. */
private fun Char.isHyphen(): Boolean = this == '-' || this == '\u2010' || this == '\u2011'
