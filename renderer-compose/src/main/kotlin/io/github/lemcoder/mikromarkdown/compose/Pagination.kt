package io.github.lemcoder.mikromarkdown.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import io.github.lemcoder.mikromarkdown.model.Block

/**
 * One thing on a page.
 *
 * Text is sliced rather than placed whole, because a paragraph out of a PDF is routinely longer than a screen — a page
 * of a paper arrives as one block — and a reader that could only break between paragraphs would put that one paragraph
 * on a page of its own and cut it off.
 */
internal sealed interface PageItem {
    /** A run of lines from a text block, addressed as a range of the string it was laid out from. */
    data class TextRun(val text: AnnotatedString, val style: TextStyle, val start: Int, val end: Int) : PageItem

    /** Anything not text, placed entire: an image, a table, a list, a rule. */
    data class Whole(val block: Block) : PageItem
}

/** What fits on one screen. */
internal class ReaderPage(val items: List<PageItem>)

/** A block reduced to what pagination needs: either laid-out text, or something placed entire. */
internal sealed interface Measurable {
    data class Text(val text: AnnotatedString, val style: TextStyle) : Measurable

    data class Whole(val block: Block, val height: Float) : Measurable
}

/**
 * Packs the document into screen-sized pages.
 *
 * Text is measured once per block and then cut at line boundaries, so a break never lands inside a line — the thing
 * that makes a naive "scroll by one screen" pager unreadable. A block that is not text is placed whole, and one that
 * cannot fit an empty page is left to overflow rather than dropped: a figure taller than the screen is still better
 * seen than not.
 */
internal fun paginate(
    blocks: List<Measurable>,
    measurer: TextMeasurer,
    width: Int,
    height: Int,
    spacing: Float,
): List<ReaderPage> {
    if (width <= 0 || height <= 0) return emptyList()

    val pages = mutableListOf<ReaderPage>()
    var current = mutableListOf<PageItem>()
    var used = 0f

    fun endPage() {
        if (current.isNotEmpty()) {
            pages += ReaderPage(current)
            current = mutableListOf()
        }
        used = 0f
    }

    fun remaining() = height - used

    for (block in blocks) {
        val gap = if (current.isEmpty()) 0f else spacing
        when (block) {
            is Measurable.Whole -> {
                // A page already started that cannot hold this ends here rather than clipping it.
                if (block.height + gap > remaining() && current.isNotEmpty()) endPage()
                current += PageItem.Whole(block.block)
                used += block.height + (if (current.size == 1) 0f else spacing)
            }

            is Measurable.Text -> {
                val layout = measurer.measure(block.text, block.style, constraints = Constraints(maxWidth = width))
                var line = 0
                var first = true
                while (line < layout.lineCount) {
                    val leading = if (first && current.isNotEmpty()) spacing else 0f
                    val fits = layout.linesFitting(from = line, budget = remaining() - leading)
                    if (fits == 0) {
                        // Nothing of this block fits what is left; try it against a whole page.
                        if (current.isEmpty()) {
                            // Not even one line fits an empty page: place a line anyway, or loop.
                            current += block.runOf(layout, line, line + 1)
                            used = height.toFloat()
                            line++
                        }
                        endPage()
                        first = true
                        continue
                    }
                    current += block.runOf(layout, line, line + fits)
                    used += leading + layout.heightOf(line, line + fits)
                    line += fits
                    first = false
                }
            }
        }
    }
    endPage()
    return pages
}

/** How many lines from [from] fit in [budget]. */
private fun TextLayoutResult.linesFitting(from: Int, budget: Float): Int {
    if (budget <= 0f) return 0
    val top = getLineTop(from)
    var count = 0
    while (from + count < lineCount && getLineBottom(from + count) - top <= budget) count++
    return count
}

private fun TextLayoutResult.heightOf(from: Int, to: Int): Float = getLineBottom(to - 1) - getLineTop(from)

private fun Measurable.Text.runOf(layout: TextLayoutResult, from: Int, to: Int): PageItem.TextRun =
    PageItem.TextRun(text, style, layout.getLineStart(from), layout.getLineEnd(to - 1, visibleEnd = true))
