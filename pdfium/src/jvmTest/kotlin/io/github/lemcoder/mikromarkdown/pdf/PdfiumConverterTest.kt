package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.StreamInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PdfiumConverterTest {

    private val fixture = File("../library/src/commonTest/resources/test_files/test.pdf")

    @Test
    fun `extracts text through the generated JNI bridges`() {
        val document = PdfiumConverter().parse(fixture.readBytes(), StreamInfo(extension = "pdf"))
        val text = document.blocks.joinToString("\n") { it.toString() }

        assertTrue(text.length > 1000, "expected a page of text, got ${text.length} characters")
        assertContains(text, "Introduction")
        // The de-hyphenation ran: pdfium reports a broken word with U+FFFE between the halves.
        assertContains(text, "confirming")
        assertTrue('￾' !in text, "unmapped glyphs should not reach the model")
    }
}
