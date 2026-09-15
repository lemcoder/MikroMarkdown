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
internal class PageText(
    val text: String,
    val hyphenWraps: IntArray,
    val images: List<PageImage> = emptyList(),
    /** The upper edge of each character's box, in page coordinates, parallel to [text]. */
    val charTops: FloatArray = FloatArray(0),
) {
    /** The text side and the image side are read separately; this joins them. */
    fun copyWith(images: List<PageImage>, charTops: FloatArray): PageText =
        PageText(text, hyphenWraps, images, charTops)

    /**
     * Where a figure belongs in the page's text.
     *
     * A page object carries no reading order, so position on the page stands in for it: the figure goes before the
     * first character that sits below its top edge, which is where a reader coming down the page would meet it.
     * Single-column text is what this gets right; two columns interleave by height and would need the column boundary
     * first.
     */
    fun placementOf(image: PageImage): Int {
        if (charTops.isEmpty()) return text.length
        val index = charTops.indexOfFirst { it < image.top }
        return if (index < 0) text.length else index
    }
}

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
    return joinToString("\n") { it.restored(hyphen, 0, it.text.length) }
}

/** The chosen hyphen for the whole document, so one page's wraps do not disagree with another's. */
internal fun List<PageText>.hyphenWritten(): Char = hyphenWrittenIn(this)

/**
 * A slice of the page, with its wraps put back.
 *
 * The slice is taken before restoring rather than after, so a cut lands on a character of the page's own text and the
 * wrap indices need no remapping.
 */
internal fun PageText.restored(hyphen: Char, from: Int, to: Int): String {
    if (from >= to) return ""
    val slice = text.substring(from, to)
    if (HYPHEN_MARKER !in slice) return slice

    val wraps = hyphenWraps.toHashSet()
    return buildString(slice.length + hyphenWraps.size) {
        for ((offset, char) in slice.withIndex()) {
            when {
                char != HYPHEN_MARKER -> append(char)
                (from + offset) in wraps -> append(hyphen).append('\n')
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
private fun hyphenWrittenIn(pages: List<PageText>): Char {
    val counts = mutableMapOf<Char, Int>()
    for (page in pages) {
        for (match in WRITTEN_HYPHEN.findAll(page.text)) {
            val hyphen = match.value[1]
            counts[hyphen] = (counts[hyphen] ?: 0) + 1
        }
    }
    return counts.maxByOrNull { it.value }?.key ?: '-'
}

/** A hyphen between two letters, which is one the document meant rather than one a line break produced. */
private val WRITTEN_HYPHEN = Regex("\\p{L}[-\u2010\u2011]\\p{L}")
