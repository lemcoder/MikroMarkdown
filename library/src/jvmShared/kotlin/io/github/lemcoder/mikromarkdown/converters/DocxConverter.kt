package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Asset
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Inline
import io.github.lemcoder.mikromarkdown.model.ListBlock
import io.github.lemcoder.mikromarkdown.model.ListItem
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import io.github.lemcoder.mikromarkdown.model.Text
import io.github.lemcoder.mikromarkdown.model.plainText
import io.github.lemcoder.mikromarkdown.model.styled
import java.util.Base64
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable

class DocxConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "docx" ||
            info.mimetype == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val docx = XWPFDocument(bytes.inputStream())
        try {
            val blocks = mutableListOf<Block>()
            val assets = mutableListOf<Asset>()
            var title: String? = docx.properties?.coreProperties?.title?.trim()?.ifBlank { null }
            val pendingListItems = mutableListOf<Pair<Int, List<Inline>>>()

            fun flushList() {
                if (pendingListItems.isEmpty()) return
                blocks += buildNestedList(pendingListItems)
                pendingListItems.clear()
            }

            for (element in docx.bodyElements) {
                when (element) {
                    is XWPFParagraph -> {
                        val content = paragraphInlines(element, assets)
                        if (content.plainText().isBlank() && content.none { it is Image }) continue

                        val level = headingLevel(element.styleID)
                        when {
                            level > 0 -> {
                                flushList()
                                if (title == null) title = content.plainText().trim()
                                blocks += Heading(level, content)
                            }

                            element.numID != null ->
                                pendingListItems += (element.numIlvl?.toInt() ?: 0).coerceAtLeast(0) to content

                            else -> {
                                flushList()
                                blocks += Paragraph(content)
                            }
                        }
                    }

                    is XWPFTable -> {
                        flushList()
                        table(element)?.let { blocks += it }
                    }
                }
            }
            flushList()

            return Document(blocks = blocks, title = title, assets = assets)
        } finally {
            docx.close()
        }
    }

    private fun paragraphInlines(para: XWPFParagraph, assets: MutableList<Asset>): List<Inline> {
        val out = mutableListOf<Inline>()
        for (run in para.runs) {
            val pictures = run.embeddedPictures
            if (pictures.isNotEmpty()) {
                for (picture in pictures) {
                    val data = picture.pictureData
                    val alt = picture.description.orEmpty()
                    if (data == null) {
                        if (alt.isNotBlank()) out += Text(alt)
                        continue
                    }
                    val mime = data.pictureTypeEnum.contentType
                    val id = data.fileName ?: "image-${assets.size + 1}"
                    assets += Asset(id = id, mediaType = mime, bytes = data.data, name = data.fileName)
                    out +=
                        Image(
                            alt = alt,
                            url = "data:$mime;base64,${Base64.getEncoder().encodeToString(data.data)}",
                            assetId = id,
                        )
                }
                continue
            }

            val text = run.text() ?: continue
            if (text.isEmpty()) continue
            if (text.isBlank()) {
                out += Text(text)
                continue
            }
            out += styled(listOf(Text(text)), bold = run.isBold, italic = run.isItalic, strike = run.isStrikeThrough)
        }
        return out
    }

    /** Rebuilds Word's flat numbering levels into nested list blocks. */
    private fun buildNestedList(items: List<Pair<Int, List<Inline>>>): ListBlock {
        var index = 0

        fun build(level: Int): List<ListItem> {
            val result = mutableListOf<ListItem>()
            while (index < items.size) {
                val (itemLevel, content) = items[index]
                when {
                    itemLevel < level -> break
                    itemLevel == level -> {
                        index++
                        val children =
                            if (index < items.size && items[index].first > level) {
                                listOf(ListBlock(ordered = false, items = build(items[index].first)))
                            } else {
                                emptyList()
                            }
                        result += ListItem(listOf(Paragraph(content)) + children)
                    }
                    // A deeper first item without a parent: promote it to this level.
                    else -> result += ListItem(listOf(ListBlock(ordered = false, items = build(itemLevel))))
                }
            }
            return result
        }

        return ListBlock(ordered = false, items = build(items.minOf { it.first }))
    }

    private fun table(table: XWPFTable): Table? {
        val rows = table.rows
        if (rows.isEmpty()) return null
        val header = rows[0].tableCells.map { TableCell(it.text.trim()) }
        val body = rows.drop(1).map { row -> row.tableCells.map { TableCell(it.text.trim()) } }
        return Table(header = header, rows = body)
    }

    private fun headingLevel(style: String?): Int {
        val s = style?.replace("\\s+".toRegex(), "") ?: return 0
        if (s.startsWith("Heading", ignoreCase = true)) {
            return s.drop(7).toIntOrNull()?.coerceIn(1, 6) ?: 0
        }
        // OOXML numeric style IDs 1-6 map directly to heading levels
        return s.toIntOrNull()?.takeIf { it in 1..6 } ?: 0
    }
}
