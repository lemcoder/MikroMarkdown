package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.model.Asset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins what [restored] does with pdfium's hyphen marker, without needing a PDF that happens to contain one. */
class PageTextTest {

    private val marker = HYPHEN_MARKER

    @Test
    fun `a confirmed wrap gets its hyphen and its line break back`() {
        val page = PageText("con${marker}firming it", intArrayOf(3))

        assertEquals("con-\nfirming it", listOf(page).restored())
    }

    @Test
    fun `the hyphen put back is the one the document writes`() {
        // U+2010 in the compound the document did not break, so U+2010 is what the wrap gets.
        val page = PageText("con${marker}firming a well\u2010known case", intArrayOf(3))

        assertEquals("con\u2010\nfirming a well\u2010known case", listOf(page).restored())
    }

    @Test
    fun `a document with no written hyphen falls back to U+002D`() {
        val page = PageText("con${marker}firming it", intArrayOf(3))

        assertEquals('-', listOf(page).restored()[3])
    }

    @Test
    fun `the hyphen is chosen across every page, not one at a time`() {
        val first = PageText("a well\u2010known case", IntArray(0))
        val second = PageText("con${marker}firming it", intArrayOf(3))

        assertEquals("a well\u2010known case\ncon\u2010\nfirming it", listOf(first, second).restored())
    }

    @Test
    fun `a marker the geometry did not confirm stands for nothing`() {
        val page = PageText("Section $marker 3", IntArray(0))

        assertEquals("Section  3", listOf(page).restored())
    }

    @Test
    fun `each marker is judged on its own`() {
        val page = PageText("con${marker}firming and Section $marker 3", intArrayOf(3))

        assertEquals("con-\nfirming and Section  3", listOf(page).restored())
    }

    @Test
    fun `text without markers is returned as it is`() {
        val page = PageText("nothing to restore", IntArray(0))

        assertEquals("nothing to restore", listOf(page).restored())
    }

    @Test
    fun `a figure is placed at the start of a line, not inside a word`() {
        // "Since" on the second line has a tall S: only its later letters sit below the figure's top edge.
        val text = "first line\nSince here"
        val page =
            PageText(
                text,
                IntArray(0),
                boxes = tops(text.length) { if (it < 11) 700f else if (it == 11) 610f else 590f },
            )

        assertEquals(11, page.placementOf(figure(top = 600f)))
    }

    @Test
    fun `a figure above all text goes first, and one below it goes last`() {
        val page = PageText("only line", IntArray(0), boxes = tops(9) { 500f })

        assertEquals(0, page.placementOf(figure(top = 800f)))
        assertEquals(9, page.placementOf(figure(top = 100f)))
    }

    @Test
    fun `an OCR fragment is not a figure, whatever its resolution`() {
        val filter = FigureFilter(pageWidth = 612f, pageHeight = 792f, pageHasText = true)

        // A vector arrow over a name, a third of an inch wide and a fifth tall.
        assertFalse(filter.keeps(left = 100f, bottom = 500f, right = 124f, top = 514f))
        // A wide but thin strip is a fragment too.
        assertFalse(filter.keeps(left = 72f, bottom = 500f, right = 540f, top = 520f))
        // A figure two inches square.
        assertTrue(filter.keeps(left = 72f, bottom = 300f, right = 216f, top = 444f))
    }

    @Test
    fun `the scan behind a page's text is not a figure, but a page with nothing else keeps it`() {
        val wholePage = floatArrayOf(0f, 0f, 612f, 792f)

        assertFalse(
            FigureFilter(612f, 792f, pageHasText = true).keeps(wholePage[0], wholePage[1], wholePage[2], wholePage[3])
        )
        assertTrue(
            FigureFilter(612f, 792f, pageHasText = false).keeps(wholePage[0], wholePage[1], wholePage[2], wholePage[3])
        )
    }

    /** Boxes that matter only for their tops. */
    private fun tops(count: Int, top: (Int) -> Float) =
        CharBoxes(count).apply {
            for (index in 0 until count) set(index, floatArrayOf(0f, top(index) - 10f, 5f, top(index)))
        }

    private fun figure(top: Float) = PageImage(Asset("figure", "image/png", ByteArray(0), "figure.png"), 1, 1, top)
}
