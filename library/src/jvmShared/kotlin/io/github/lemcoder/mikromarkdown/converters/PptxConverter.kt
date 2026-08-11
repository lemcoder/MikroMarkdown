package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.HtmlComment
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.ListBlock
import io.github.lemcoder.mikromarkdown.model.ListItem
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import io.github.lemcoder.mikromarkdown.model.Text
import org.apache.poi.sl.usermodel.Placeholder
import org.apache.poi.sl.usermodel.Shape
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFChart
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFSimpleShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAreaSer
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxDataSource
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarSer
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineSer
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumDataSource
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPieSer
import org.openxmlformats.schemas.drawingml.x2006.chart.CTScatterSer
import org.openxmlformats.schemas.drawingml.x2006.chart.CTSerTx
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture

public class PptxConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pptx" ||
            info.mimetype == "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val slideShow = XMLSlideShow(bytes.inputStream())
        try {
            val blocks = mutableListOf<Block>()
            var title: String? = null

            for ((index, slide) in slideShow.slides.withIndex()) {
                blocks += HtmlComment("Slide number: ${index + 1}")
                blocks += shapeBlocks(slide.shapes) { if (index == 0 && title == null) title = it }

                val notes =
                    slide.notes
                        ?.shapes
                        ?.filterIsInstance<XSLFTextShape>()
                        ?.filter { (it as? XSLFSimpleShape)?.placeholder != Placeholder.SLIDE_IMAGE }
                        ?.joinToString("\n") { it.text }
                        ?.trim()
                        .orEmpty()
                if (notes.isNotBlank()) {
                    blocks += Heading(3, listOf(Text("Notes:")))
                    blocks += notes.lines().filter { it.isNotBlank() }.map { Paragraph(listOf(Text(it.trim()))) }
                }
            }

            return Document(blocks = blocks, title = title)
        } finally {
            slideShow.close()
        }
    }

    private fun shapeBlocks(shapes: Iterable<Shape<*, *>>, onTitle: (String) -> Unit): List<Block> {
        val blocks = mutableListOf<Block>()
        for (shape in shapes) {
            when {
                shape is XSLFGroupShape -> blocks += shapeBlocks(shape.shapes, onTitle)

                shape is XSLFTextShape -> {
                    val text = shape.text.trim()
                    if (text.isBlank()) continue

                    val placeholder = (shape as? XSLFSimpleShape)?.placeholder
                    if (placeholder == Placeholder.TITLE || placeholder == Placeholder.CENTERED_TITLE) {
                        blocks += Heading(1, listOf(Text(text)))
                        onTitle(text)
                        continue
                    }

                    // Consecutive bullet paragraphs become one list; plain ones stay paragraphs.
                    val bullets = mutableListOf<ListItem>()
                    fun flushBullets() {
                        if (bullets.isEmpty()) return
                        blocks += ListBlock(ordered = false, items = bullets.toList())
                        bullets.clear()
                    }
                    for (para in shape.textParagraphs) {
                        val paraText = para.text.trim()
                        if (paraText.isBlank()) continue
                        if (para.isBullet) {
                            bullets += ListItem(listOf(Paragraph(listOf(Text(paraText)))))
                        } else {
                            flushBullets()
                            blocks += Paragraph(listOf(Text(paraText)))
                        }
                    }
                    flushBullets()
                }

                shape is XSLFPictureShape -> {
                    val description = (shape.xmlObject as? CTPicture)?.nvPicPr?.cNvPr?.descr.orEmpty()
                    val alt = description.ifBlank { shape.shapeName }
                    val filename = shape.shapeName.replace(Regex("\\W"), "") + ".jpg"
                    blocks += Paragraph(listOf(Image(alt, filename)))
                }

                shape is XSLFGraphicFrame && shape.hasChart() -> blocks += chartBlocks(shape.chart)

                shape is XSLFTable -> table(shape)?.let { blocks += it }
            }
        }
        return blocks
    }

    private fun chartBlocks(chart: XSLFChart): List<Block> {
        val blocks = mutableListOf<Block>()
        blocks += Heading(3, listOf(Text(listOfNotNull("Chart", chartTitle(chart)).joinToString(": "))))

        val series =
            try {
                seriesOf(chart)
            } catch (_: Exception) {
                blocks += Paragraph(listOf(Text("[unsupported chart]")))
                return blocks
            }
        if (series.isEmpty()) return blocks

        val rowCount = series.maxOf { it.categories.size }
        blocks +=
            Table(
                header = (listOf("Category") + series.map { it.name }).map { TableCell(it) },
                rows =
                    (0 until rowCount).map { row ->
                        val category = series.first().categories.getOrElse(row) { "" }
                        (listOf(category) + series.map { it.values.getOrElse(row) { "" } }).map { TableCell(it) }
                    },
            )
        return blocks
    }

    private fun chartTitle(chart: XSLFChart): String? =
        try {
            val ctChart = chart.ctChart
            if (!ctChart.isSetTitle) {
                null
            } else {
                val tx = ctChart.title?.tx
                when {
                    tx?.isSetRich == true ->
                        tx.rich.pList.flatMap { p -> p.rList.map { r -> r.t.orEmpty() } }.joinToString("")
                    tx?.isSetStrRef == true -> tx.strRef?.strCache?.ptList?.firstOrNull()?.v
                    else -> null
                }?.ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }

    private data class Series(val name: String, val categories: List<String>, val values: List<String>)

    private fun seriesOf(chart: XSLFChart): List<Series> {
        val plot = chart.ctChart.plotArea
        // bar/bar3D, line/line3D and area/area3D each share one generated series type.
        val all =
            plot.barChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.bar3DChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.lineChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.line3DChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.areaChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.area3DChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.scatterChartList.flatMap { it.serList.map { s -> s.toSeries() } } +
                plot.pieChartList.flatMap { it.serList.map { s -> s.toSeries() } }
        return all.filter { it.categories.isNotEmpty() || it.values.isNotEmpty() }
    }

    private fun CTBarSer.toSeries() =
        series(if (isSetTx) tx else null, if (isSetCat) cat else null, if (isSetVal) `val` else null)

    private fun CTLineSer.toSeries() =
        series(if (isSetTx) tx else null, if (isSetCat) cat else null, if (isSetVal) `val` else null)

    private fun CTAreaSer.toSeries() =
        series(if (isSetTx) tx else null, if (isSetCat) cat else null, if (isSetVal) `val` else null)

    private fun CTPieSer.toSeries() =
        series(if (isSetTx) tx else null, if (isSetCat) cat else null, if (isSetVal) `val` else null)

    private fun CTScatterSer.toSeries() =
        series(if (isSetTx) tx else null, if (isSetXVal) xVal else null, if (isSetYVal) yVal else null)

    private fun series(tx: CTSerTx?, categories: CTAxDataSource?, values: CTNumDataSource?) =
        Series(seriesName(tx), categoryValues(categories), numericValues(values))

    private fun categoryValues(cat: CTAxDataSource?): List<String> =
        when {
            cat == null -> emptyList()
            cat.isSetStrRef -> cat.strRef?.strCache?.ptList?.sortedBy { it.idx }?.map { it.v } ?: emptyList()
            cat.isSetNumRef -> cat.numRef?.numCache?.ptList?.sortedBy { it.idx }?.map { it.v.orEmpty() } ?: emptyList()
            cat.isSetNumLit -> cat.numLit?.ptList?.sortedBy { it.idx }?.map { it.v.orEmpty() } ?: emptyList()
            cat.isSetStrLit -> cat.strLit?.ptList?.sortedBy { it.idx }?.map { it.v } ?: emptyList()
            else -> emptyList()
        }

    private fun numericValues(v: CTNumDataSource?): List<String> =
        when {
            v == null -> emptyList()
            v.isSetNumRef -> v.numRef?.numCache?.ptList?.sortedBy { it.idx }?.map { it.v.orEmpty() } ?: emptyList()
            v.isSetNumLit -> v.numLit?.ptList?.sortedBy { it.idx }?.map { it.v.orEmpty() } ?: emptyList()
            else -> emptyList()
        }

    private fun seriesName(tx: CTSerTx?): String =
        when {
            tx == null -> ""
            tx.isSetV -> tx.v
            tx.isSetStrRef -> tx.strRef?.strCache?.ptList?.firstOrNull()?.v.orEmpty()
            else -> ""
        }

    private fun table(table: XSLFTable): Table? {
        val rows = table.rows
        if (rows.isEmpty()) return null
        return Table(
            header = rows[0].cells.map { TableCell(it.text.trim()) },
            rows = rows.drop(1).map { row -> row.cells.map { TableCell(it.text.trim()) } },
        )
    }
}
