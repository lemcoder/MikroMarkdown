package io.github.lemcoder.mikromarkdown.pdf

/**
 * One page's text, and the line breaks pdfium collapsed while producing it.
 *
 * A hyphenated wrap does not reach [text] as a hyphen and a line break. pdfium recognises the wrap, joins the two
 * lines, and leaves [HYPHEN_MARKER] standing where the hyphen was — so `con-` at the right margin and `firming` at the
 * start of the next line arrive adjacent, with the marker between them and nothing to say a line ever ended there.
 *
 * [hyphenWraps] are the marker positions that `FPDFText_GetCharBox` confirms: the character after the marker really
 * does begin a lower line. That is the one thing the geometry answers outright, and it is what [restored] needs.
 */
internal class PageText(val text: String, val hyphenWraps: IntArray)

/** pdfium's stand-in for a hyphen it removed at a line break. */
internal const val HYPHEN_MARKER: Char = '\uFFFE'

/**
 * Puts the wrap back the way the page shows it.
 *
 * Whether the hyphen was the author's (`chat-optimized`, broken across lines by chance) or the typesetter's
 * (`con-firming`) is not something the geometry can answer: both sit at the right margin with the next line below. So
 * the hyphen is written back as the page had it and the question is left open — [plainTextBlocks]
 * [io.github.lemcoder.mikromarkdown.model.plainTextBlocks] closes the break without touching it.
 *
 * A marker the geometry did not confirm was not a wrap; there is no character it stands for, so nothing is written.
 */
internal fun List<PageText>.restored(): String {
    val hyphen = hyphenWritten()
    return joinToString("\n") { it.restored(hyphen) }
}

private fun PageText.restored(hyphen: Char): String {
    if (HYPHEN_MARKER !in text) return text

    val wraps = hyphenWraps.toHashSet()
    return buildString(text.length + hyphenWraps.size) {
        for ((index, char) in text.withIndex()) {
            when {
                char != HYPHEN_MARKER -> append(char)
                index in wraps -> append(hyphen).append('\n')
            }
        }
    }
}

/**
 * The hyphen this document writes between words.
 *
 * The marker does not say which character it replaced — pdfium keeps no record of it, and the one call that could
 * answer, `FPDFTextObj_GetText`, needs a header we do not bind. So the document is asked instead: whichever hyphen it
 * uses in the compounds it did not break is the one to put back, rather than U+002D on a page set in U+2010.
 */
private fun List<PageText>.hyphenWritten(): Char {
    val counts = mutableMapOf<Char, Int>()
    for (page in this) {
        for (match in WRITTEN_HYPHEN.findAll(page.text)) {
            val hyphen = match.value[1]
            counts[hyphen] = (counts[hyphen] ?: 0) + 1
        }
    }
    return counts.maxByOrNull { it.value }?.key ?: '-'
}

/** A hyphen between two letters, which is one the document meant rather than one a line break produced. */
private val WRITTEN_HYPHEN = Regex("\\p{L}[-\u2010\u2011]\\p{L}")
