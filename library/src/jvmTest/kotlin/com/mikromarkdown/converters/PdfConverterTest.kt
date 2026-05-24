package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertTrue

class PdfConverterTest {
    private val converter = PdfConverter()
    private val info = StreamInfo(extension = "pdf")

    @Test
    fun `accepts pdf extension`() {
        assertTrue(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `text content is extracted from PDF`() {
        val baos = buildPdf("Hello PDF World")
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("Hello PDF World"), "Expected text in:\n${result.markdown}")
    }

    @Test
    fun `multiple lines extracted`() {
        val baos = buildPdf("Line One", "Line Two")
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("Line One"))
        assertTrue(result.markdown.contains("Line Two"))
    }

    private fun buildPdf(vararg lines: String): ByteArrayOutputStream {
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val contentStream = PDPageContentStream(doc, page)
        contentStream.beginText()
        contentStream.setFont(font, 12f)
        contentStream.newLineAtOffset(100f, 700f)
        lines.forEachIndexed { index, line ->
            if (index > 0) contentStream.newLineAtOffset(0f, -20f)
            contentStream.showText(line)
        }
        contentStream.endText()
        contentStream.close()
        val baos = ByteArrayOutputStream()
        doc.save(baos)
        doc.close()
        return baos
    }
}
