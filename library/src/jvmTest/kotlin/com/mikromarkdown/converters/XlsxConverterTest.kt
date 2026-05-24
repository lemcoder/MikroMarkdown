package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertTrue

class XlsxConverterTest {
    private val converter = XlsxConverter()
    private val info = StreamInfo(extension = "xlsx")

    @Test
    fun `accepts xlsx extension`() {
        assertTrue(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `sheet name becomes heading`() {
        val baos = buildWorkbook {
            val sheet = createSheet("Inventory")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("Item")
            header.createCell(1).setCellValue("Count")
            val row = sheet.createRow(1)
            row.createCell(0).setCellValue("Widget")
            row.createCell(1).setCellValue(42.0)
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("## Inventory"), "Expected sheet heading in:\n${result.markdown}")
    }

    @Test
    fun `data converts to GFM table`() {
        val baos = buildWorkbook {
            val sheet = createSheet("Sheet1")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("Name")
            header.createCell(1).setCellValue("Score")
            val row = sheet.createRow(1)
            row.createCell(0).setCellValue("Alice")
            row.createCell(1).setCellValue(95.0)
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("| Name | Score |"), "Expected header in:\n${result.markdown}")
        assertTrue(result.markdown.contains("| --- | --- |"), "Expected separator in:\n${result.markdown}")
        assertTrue(result.markdown.contains("| Alice |"), "Expected data row in:\n${result.markdown}")
    }

    @Test
    fun `integer values formatted without decimal`() {
        val baos = buildWorkbook {
            val sheet = createSheet("Data")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("Count")
            val row = sheet.createRow(1)
            row.createCell(0).setCellValue(100.0)
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("| 100 |"), "Expected '100' not '100.0' in:\n${result.markdown}")
    }

    private fun buildWorkbook(block: XSSFWorkbook.() -> Unit): ByteArrayOutputStream {
        val workbook = XSSFWorkbook()
        workbook.block()
        val baos = ByteArrayOutputStream()
        workbook.write(baos)
        workbook.close()
        return baos
    }
}
