package io.github.lemcoder.mikromarkdown.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the order [inReadingOrder] rebuilds, on pages laid out by hand: each run is placed where a PDF would have it,
 * and the runs are given in the order a producer might have written them.
 */
class ReadingOrderTest {

    @Test
    fun `a contents page written column by column is read row by row`() {
        // As an OCR layer writes it: every entry number, then every title, then every page number.
        val page =
            page(
                Run("1.", left = 72f, top = 700f),
                Run("2.", left = 72f, top = 680f),
                Run("Definitions", left = 100f, top = 700f),
                Run("Addition of vectors . . . . .", left = 100f, top = 680f),
                Run("1", left = 500f, top = 700f),
                Run("2", left = 500f, top = 680f),
            )

        assertEquals("1. Definitions 1\n\n2. Addition of vectors . . . . . 2", page.inReadingOrder().text)
    }

    @Test
    fun `two text columns are read one after the other, not across`() {
        val left = (0 until 3).map { Run("left line $it", left = 72f, top = 700f - it * 14f, width = 200f) }
        val right = (0 until 3).map { Run("right line $it", left = 320f, top = 700f - it * 14f, width = 200f) }
        val page = page(*(left + right).toTypedArray())

        assertEquals(
            "left line 0\nleft line 1\nleft line 2\n\nright line 0\nright line 1\nright line 2",
            page.inReadingOrder().text,
        )
    }

    @Test
    fun `prose keeps its lines, and a wider gap ends the paragraph`() {
        val page =
            page(
                Run("first line of prose", left = 72f, top = 700f, width = 400f),
                Run("second line of prose", left = 72f, top = 686f, width = 400f),
                Run("a new paragraph", left = 72f, top = 650f, width = 400f),
            )

        assertEquals("first line of prose\nsecond line of prose\n\na new paragraph", page.inReadingOrder().text)
    }

    @Test
    fun `a removed hyphen joins its two lines with nothing between them`() {
        val page =
            page(
                Run("con$HYPHEN_MARKER", left = 72f, top = 700f, width = 400f, joined = true),
                Run("firming it", left = 72f, top = 686f, width = 400f),
            )

        val ordered = page.inReadingOrder()
        assertEquals("con${HYPHEN_MARKER}firming it", ordered.text)
        assertEquals(listOf(3), ordered.hyphenWraps.toList(), "the wrap must follow its marker to the new index")
    }

    @Test
    fun `a page without boxes is left as it came`() {
        val page = PageText("as\r\nit came", IntArray(0))

        assertEquals("as\r\nit came", page.inReadingOrder().text)
    }

    /** A run of text, ten points high, its characters spread evenly over [width]. */
    private class Run(
        val text: String,
        val left: Float,
        val top: Float,
        val width: Float = text.length * 6f,
        /** pdfium writes no break after a removed hyphen: the next run follows directly. */
        val joined: Boolean = false,
    )

    /** The page as pdfium would return it: runs in the order given, a line break after each unless [Run.joined]. */
    private fun page(vararg runs: Run): PageText {
        val text = StringBuilder()
        val placed = mutableListOf<FloatArray?>()
        val wraps = mutableListOf<Int>()
        for ((index, run) in runs.withIndex()) {
            val step = run.width / run.text.length
            for ((at, char) in run.text.withIndex()) {
                if (char == HYPHEN_MARKER) wraps += text.length
                text.append(char)
                val left = run.left + at * step
                placed += if (char == ' ') null else floatArrayOf(left, run.top - 10f, left + step, run.top)
            }
            if (index < runs.lastIndex && !run.joined) {
                text.append("\r\n")
                placed += null
                placed += null
            }
        }
        val boxes = CharBoxes(placed.size).apply { placed.forEachIndexed { at, box -> if (box != null) set(at, box) } }
        return PageText(text.toString(), wraps.toIntArray(), boxes = boxes)
    }
}
