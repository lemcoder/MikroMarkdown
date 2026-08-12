package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import io.github.lemcoder.mikromarkdown.model.Text
import kotlin.math.floor
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook

public class XlsxConverter : DocumentConverter {
    // Constructing a converter must not load POI: accepts() only looks at the extension.
    private val formatter by lazy { DataFormatter() }

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "xlsx" ||
            info.mimetype == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val workbook = XSSFWorkbook(bytes.inputStream())
        try {
            val blocks = mutableListOf<Block>()

            for (sheet in workbook) {
                val rows = sheet.toList()
                if (rows.isEmpty()) continue

                val columns = rows.maxOf { it.lastCellNum.toInt().coerceAtLeast(0) }
                if (columns == 0) continue

                blocks += Heading(2, listOf(Text(sheet.sheetName)))
                blocks +=
                    Table(
                        header = (0 until columns).map { TableCell(cellValue(rows[0].getCell(it))) },
                        rows =
                            rows.drop(1).map { row -> (0 until columns).map { TableCell(cellValue(row.getCell(it))) } },
                    )
            }

            return Document(blocks = blocks)
        } finally {
            workbook.close()
        }
    }

    private fun cellValue(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                val v = cell.numericCellValue
                if (v == floor(v) && !v.isInfinite()) v.toLong().toString() else formatter.formatCellValue(cell)
            }
            CellType.BLANK -> ""
            else -> formatter.formatCellValue(cell).trim()
        }
    }
}
