package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFSimpleShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxDataSource
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumDataSource
import org.openxmlformats.schemas.drawingml.x2006.chart.CTSerTx
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
            sb.appendLine("<!-- Slide number: ${index + 1} -->")
            processShapes(slide.shapes, sb, index == 0) { t -> if (title == null) title = t }

            // Speaker notes
            val notes = slide.notes
            if (notes != null) {
                val notesText = notes.shapes
                    .filterIsInstance<XSLFTextShape>()
                    .filter { (it as? XSLFSimpleShape)?.placeholder != Placeholder.SLIDE_IMAGE }
                    .joinToString("\n") { it.text }
                    .trim()
                if (notesText.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine("### Notes:")
                    sb.appendLine(notesText)
                }
            }

            sb.appendLine()
        }

        slideShow.close()
        return ConversionResult(markdown = sb.toString(), title = title)
    }

    private fun processShapes(
        shapes: Iterable<org.apache.poi.sl.usermodel.Shape<*, *>>,
        sb: StringBuilder,
        isFirstSlide: Boolean,
        onTitle: (String) -> Unit,
    ) {
        for (shape in shapes) {
            when {
                shape is XSLFGroupShape -> processShapes(shape.shapes, sb, isFirstSlide, onTitle)
                shape is XSLFTextShape -> {
                    val text = shape.text.trim()
                    if (text.isBlank()) continue

                    val placeholder = (shape as? XSLFSimpleShape)?.placeholder
                    val isTitle = placeholder == Placeholder.TITLE ||
                                  placeholder == Placeholder.CENTERED_TITLE

                    if (isTitle) {
                        sb.appendLine("# $text")
                        if (isFirstSlide) onTitle(text)
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
                    val descr = (shape.xmlObject as? CTPicture)?.nvPicPr?.cNvPr?.descr ?: ""
                    val altText = if (descr.isNotBlank()) descr else shape.shapeName
                    val filename = shape.shapeName.replace(Regex("\\W"), "") + ".jpg"
                    sb.appendLine("![$altText]($filename)")
                }
                shape is XSLFGraphicFrame && shape.hasChart() -> {
                    sb.append(convertChart(shape.chart))
                }
                shape is XSLFTable -> sb.appendLine(convertTable(shape))
            }
        }
    }

    private fun convertChart(chart: org.apache.poi.xslf.usermodel.XSLFChart): String {
        val sb = StringBuilder()
        sb.append("\n\n### Chart")

        try {
            val ctChart = chart.ctChart
            if (ctChart.isSetTitle) {
                val tx = ctChart.title?.tx
                val titleText = when {
                    tx?.isSetRich == true ->
                        tx.rich.pList.flatMap { p -> p.rList.map { r -> r.t ?: "" } }.joinToString("")
                    tx?.isSetStrRef == true ->
                        tx.strRef?.strCache?.ptList?.firstOrNull()?.v ?: ""
                    else -> ""
                }
                if (titleText.isNotBlank()) sb.append(": $titleText")
            }
        } catch (_: Exception) {}

        sb.append("\n\n")

        data class SeriesData(val name: String, val categories: List<String>, val values: List<String>)

        fun catStrings(cat: CTAxDataSource?): List<String> {
            if (cat == null) return emptyList()
            return when {
                cat.isSetStrRef -> cat.strRef?.strCache?.ptList?.sortedBy { it.idx }?.map { it.v } ?: emptyList()
                cat.isSetNumRef -> cat.numRef?.numCache?.ptList?.sortedBy { it.idx }?.map { it.v ?: "" } ?: emptyList()
                cat.isSetNumLit -> cat.numLit?.ptList?.sortedBy { it.idx }?.map { it.v ?: "" } ?: emptyList()
                cat.isSetStrLit -> cat.strLit?.ptList?.sortedBy { it.idx }?.map { it.v } ?: emptyList()
                else -> emptyList()
            }
        }

        fun valStrings(v: CTNumDataSource?): List<String> {
            if (v == null) return emptyList()
            return when {
                v.isSetNumRef -> v.numRef?.numCache?.ptList?.sortedBy { it.idx }?.map { it.v ?: "" } ?: emptyList()
                v.isSetNumLit -> v.numLit?.ptList?.sortedBy { it.idx }?.map { it.v ?: "" } ?: emptyList()
                else -> emptyList()
            }
        }

        fun serTitle(tx: CTSerTx?): String = when {
            tx == null -> ""
            tx.isSetV -> tx.v
            tx.isSetStrRef -> tx.strRef?.strCache?.ptList?.firstOrNull()?.v ?: ""
            else -> ""
        }

        val seriesList = mutableListOf<SeriesData>()
        try {
            val plotArea = chart.ctChart.plotArea

            for (c in plotArea.barChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetCat) s.cat else null),
                    valStrings(if (s.isSetVal) s.`val` else null)))
            for (c in plotArea.bar3DChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetCat) s.cat else null),
                    valStrings(if (s.isSetVal) s.`val` else null)))
            for (c in plotArea.lineChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetCat) s.cat else null),
                    valStrings(if (s.isSetVal) s.`val` else null)))
            for (c in plotArea.line3DChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetCat) s.cat else null),
                    valStrings(if (s.isSetVal) s.`val` else null)))
            for (c in plotArea.areaChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetCat) s.cat else null),
                    valStrings(if (s.isSetVal) s.`val` else null)))
            for (c in plotArea.area3DChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetCat) s.cat else null),
                    valStrings(if (s.isSetVal) s.`val` else null)))
            for (c in plotArea.scatterChartList)
                for (s in c.serList) seriesList.add(SeriesData(
                    serTitle(if (s.isSetTx) s.tx else null),
                    catStrings(if (s.isSetXVal) s.xVal else null),
                    valStrings(if (s.isSetYVal) s.yVal else null)))

        } catch (_: Exception) {
            return sb.append("[unsupported chart]\n\n").toString()
        }

        if (seriesList.isEmpty()) return sb.toString()

        val header = listOf("Category") + seriesList.map { it.name }
        sb.appendLine("| " + header.joinToString(" | ") + " |")
        sb.appendLine("|" + "---|".repeat(header.size))

        val numRows = seriesList.maxOfOrNull { it.categories.size } ?: 0
        for (i in 0 until numRows) {
            val cat = seriesList.first().categories.getOrElse(i) { "" }
            val vals = seriesList.map { it.values.getOrElse(i) { "" } }
            sb.appendLine("| " + (listOf(cat) + vals).joinToString(" | ") + " |")
        }

        return sb.toString()
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
