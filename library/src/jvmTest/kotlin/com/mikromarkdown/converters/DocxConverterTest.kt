package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertTrue

class DocxConverterTest {
    private val converter = DocxConverter()
    private val info = StreamInfo(extension = "docx")

    @Test
    fun `accepts docx extension`() {
        assertTrue(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `heading 1 converts to ATX heading`() {
        val baos = buildDoc {
            val para = createParagraph()
            para.setStyle("Heading1")
            para.createRun().setText("My Heading")
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("# My Heading"), "Expected '# My Heading' in:\n${result.markdown}")
    }

    @Test
    fun `bold text converts to double asterisks`() {
        val baos = buildDoc {
            val para = createParagraph()
            val run = para.createRun()
            run.isBold = true
            run.setText("Bold Text")
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("**Bold Text**"), "Expected '**Bold Text**' in:\n${result.markdown}")
    }

    @Test
    fun `table converts to GFM table`() {
        val baos = buildDoc {
            val table = createTable(2, 2)
            table.getRow(0).getCell(0).setText("Name")
            table.getRow(0).getCell(1).setText("Value")
            table.getRow(1).getCell(0).setText("Alpha")
            table.getRow(1).getCell(1).setText("1")
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("| Name | Value |"), "Expected table header in:\n${result.markdown}")
        assertTrue(result.markdown.contains("| Alpha | 1 |"), "Expected table row in:\n${result.markdown}")
    }

    private fun buildDoc(block: XWPFDocument.() -> Unit): ByteArrayOutputStream {
        val doc = XWPFDocument()
        doc.block()
        val baos = ByteArrayOutputStream()
        doc.write(baos)
        doc.close()
        return baos
    }
}
