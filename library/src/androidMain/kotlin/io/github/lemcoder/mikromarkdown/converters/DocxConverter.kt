package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable

class DocxConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "docx" ||
               info.mimetype == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val doc = XWPFDocument(bytes.inputStream())
        val sb = StringBuilder()
        var title: String? = null

        for (element in doc.bodyElements) {
            when (element) {
                is XWPFParagraph -> {
                    val md = convertParagraph(element)
                    if (md.isNotBlank()) {
                        if (title == null && headingLevel(element.styleID) > 0) {
                            title = element.text
                        }
                        sb.appendLine(md)
                    }
                }
                is XWPFTable -> {
                    val tableMd = convertTable(element)
                    if (tableMd.isNotBlank()) {
                        sb.appendLine(tableMd)
                    }
                }
            }
        }

        doc.close()
        return ConversionResult(markdown = sb.toString(), title = title)
    }

    private fun convertParagraph(para: XWPFParagraph): String {
        val rawText = para.runs.joinToString("") { run ->
            var text = run.text() ?: ""
            if (text.isBlank()) return@joinToString text
            when {
                run.isBold && run.isItalic -> "***$text***"
                run.isBold -> "**$text**"
                run.isItalic -> "*$text*"
                else -> text
            }
        }

        if (rawText.isBlank()) return ""

        val level = headingLevel(para.styleID)
        val isListItem = para.numID != null

        return when {
            level > 0 -> "${"#".repeat(level)} $rawText"
            isListItem -> {
                val indent = "  ".repeat((para.numIlvl?.toInt() ?: 0).coerceAtLeast(0))
                "$indent- $rawText"
            }
            else -> rawText
        }
    }

    private fun convertTable(table: XWPFTable): String {
        val rows = table.rows
        if (rows.isEmpty()) return ""

        val sb = StringBuilder()
        val header = rows[0].tableCells.map { it.text.replace("|", "\\|") }
        sb.appendLine(header.joinToString(" | ", "| ", " |"))
        sb.appendLine(header.map { "---" }.joinToString(" | ", "| ", " |"))

        for (i in 1 until rows.size) {
            val cells = rows[i].tableCells.map { it.text.replace("|", "\\|") }
            sb.appendLine(cells.joinToString(" | ", "| ", " |"))
        }

        return sb.toString().trimEnd()
    }

    private fun headingLevel(style: String?): Int {
        val s = style?.replace("\\s+".toRegex(), "") ?: return 0
        if (s.startsWith("Heading", ignoreCase = true)) {
            return s.drop(7).toIntOrNull()?.coerceIn(1, 6) ?: 0
        }
        return s.toIntOrNull()?.takeIf { it in 1..6 } ?: 0
    }
}
