package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFSimpleShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture

class PptxConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pptx" ||
               info.mimetype == "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val slideShow = XMLSlideShow(bytes.inputStream())
        val sb = StringBuilder()
        var title: String? = null

        for ((index, slide) in slideShow.slides.withIndex()) {
            sb.appendLine("## Slide ${index + 1}")
            sb.appendLine()

            for (shape in slide.shapes) {
                when {
                    shape is XSLFTextShape -> {
                        val text = shape.text.trim()
                        if (text.isBlank()) continue

                        val placeholder = (shape as? XSLFSimpleShape)?.placeholder
                        val isTitle = placeholder == Placeholder.TITLE ||
                                      placeholder == Placeholder.CENTERED_TITLE

                        if (isTitle) {
                            sb.appendLine("### $text")
                            if (title == null && index == 0) title = text
                        } else {
                            for (para in shape.textParagraphs) {
                                val paraText = para.text.trim()
                                if (paraText.isBlank()) continue
                                if (para.isBullet) sb.appendLine("- $paraText")
                                else sb.appendLine(paraText)
                            }
                        }
                    }
                    shape is XSLFPictureShape -> {
                        val descr = (shape.xmlObject as? CTPicture)?.nvPicPr?.cNvPr?.descr
                        if (!descr.isNullOrBlank()) sb.appendLine(descr)
                    }
                    shape is XSLFTable -> sb.appendLine(convertTable(shape))
                }
            }
            sb.appendLine()
        }

        slideShow.close()
        return ConversionResult(markdown = sb.toString(), title = title)
    }

    private fun convertTable(table: XSLFTable): String {
        val rows = table.rows
        if (rows.isEmpty()) return ""

        val sb = StringBuilder()
        val header = rows[0].cells.map { it.text.trim().replace("|", "\\|") }
        sb.appendLine(header.joinToString(" | ", "| ", " |"))
        sb.appendLine(header.map { "---" }.joinToString(" | ", "| ", " |"))

        for (i in 1 until rows.size) {
            val cells = rows[i].cells.map { it.text.trim().replace("|", "\\|") }
            sb.appendLine(cells.joinToString(" | ", "| ", " |"))
        }

        return sb.toString().trimEnd()
    }
}
