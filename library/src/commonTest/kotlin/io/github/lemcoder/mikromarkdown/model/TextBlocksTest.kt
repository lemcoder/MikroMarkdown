package io.github.lemcoder.mikromarkdown.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins how a trailing hyphen at a line break is read, and the paragraph structure it must not disturb. */
class TextBlocksTest {

    /** The page break a PDF text extractor leaves behind. */
    private val formFeed = "\u000C"

    private fun paragraphs(text: String, reflow: Boolean = true): List<String> =
        plainTextBlocks(text, reflow).map { (it as Paragraph).content.plainText() }

    private fun onlyParagraph(text: String, reflow: Boolean = true): String = paragraphs(text, reflow).single()

    @Test
    fun `a hyphen at a line break keeps its hyphen and loses the break`() {
        val text = "an inter-\nnational body\nand nothing else"

        // The page meant "international". Which kind of hyphen this was cannot be known, so it is left where it is.
        assertEquals("an inter-national body and nothing else", onlyParagraph(text))
    }

    @Test
    fun `a compound broken across lines survives intact`() {
        val text = "a well-\nknown case.\nIt reads the same as any other."

        assertEquals("a well-known case. It reads the same as any other.", onlyParagraph(text))
    }

    @Test
    fun `a line break with no hyphen is the space the wrap replaced`() {
        val text = "an ordinary\nwrapped line"

        assertEquals("an ordinary wrapped line", onlyParagraph(text))
    }

    @Test
    fun `a trailing hyphen with no letter before it is not a word break`() {
        val text = "a dash -\nthen more"

        assertEquals("a dash - then more", onlyParagraph(text))
    }

    @Test
    fun `a form feed separates paragraphs rather than merging the pages around it`() {
        val text = "End of page one.${formFeed}Start of page two."

        assertEquals(listOf("End of page one.", "Start of page two."), paragraphs(text))
    }

    @Test
    fun `a U+2010 hyphen is a word break too and keeps the character the document wrote`() {
        val text = "an inter\u2010\nnational body\nand nothing else"

        assertEquals("an inter\u2010national body and nothing else", onlyParagraph(text))
    }

    @Test
    fun `without reflow the source layout is kept`() {
        val text = "an inter-\nnational body"

        assertEquals(listOf("an inter-\nnational body"), paragraphs(text, reflow = false))
    }

    @Test
    fun `a paragraph ending mid-word continues into the next`() {
        val text = "a word broken by a page-\n\nbreak and nothing else"

        assertEquals(listOf("a word broken by a page-break and nothing else"), paragraphs(text))
    }
}
