package io.github.lemcoder.mikromarkdown.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
