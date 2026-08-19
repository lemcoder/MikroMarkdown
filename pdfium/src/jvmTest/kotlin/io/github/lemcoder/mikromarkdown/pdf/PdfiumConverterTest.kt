package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.StreamInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfiumConverterTest {

    private val fixture = File("../library/src/commonTest/resources/test_files/test.pdf")

    @Test
    fun `extracts text through the generated JNI bridges`() {
        val document = PdfiumConverter().parse(fixture.readBytes(), StreamInfo(extension = "pdf"))
        val text = document.blocks.joinToString("\n") { it.toString() }

        assertTrue(text.length > 1000, "expected a page of text, got ${text.length} characters")
        assertContains(text, "Introduction")
        // A wrap the character boxes confirmed: the break is gone and the hyphen the page shows is still there.
        assertContains(text, "con-firming")
        // The other kind, the author's own, comes through the same way rather than fused into "chatoptimized".
        assertContains(text, "chat-optimized")
        assertTrue(HYPHEN_MARKER !in text, "pdfium's hyphen marker should not reach the model")
    }

    /**
     * Every marker in this paper is a wrap, and the character boxes say so.
     *
     * The assertion is the geometry's, not the text's: it is what separates "pdfium removed a hyphen here" from "the
     * line ended here", and only the second one earns a break back.
     */
    @Test
    fun `every hyphen marker in the fixture is confirmed by its character box`() {
        val pages = extractPages(fixture.readBytes())

        val markers = pages.sumOf { page -> page.text.count { it == HYPHEN_MARKER } }
        val confirmed = pages.sumOf { it.hyphenWraps.size }

        assertTrue(markers > 0, "the fixture is supposed to contain hyphenated wraps")
        assertEquals(markers, confirmed, "a marker the geometry did not confirm would be dropped silently")
    }
}
