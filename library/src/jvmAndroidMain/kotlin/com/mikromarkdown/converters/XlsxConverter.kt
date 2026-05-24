package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

class XlsxConverter : DocumentConverter {
    private val formatter = DataFormatter()

    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension == "xlsx" ||
               info.mimetype == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val workbook = XSSFWorkbook(stream)
        val sb = StringBuilder()

        for (sheet in workbook) {
            val rows = sheet.toList()
            if (rows.isEmpty()) continue

            val maxCols = rows.maxOf { it.lastCellNum.toInt().coerceAtLeast(0) }
            if (maxCols == 0) continue

            sb.appendLine("## ${sheet.sheetName}")
            sb.appendLine()

            val headerCells = (0 until maxCols).map { col ->
                cellValue(rows[0].getCell(col)).replace("|", "\\|")
            }
            sb.appendLine(headerCells.joinToString(" | ", "| ", " |"))
            sb.appendLine(headerCells.map { "---" }.joinToString(" | ", "| ", " |"))

            for (i in 1 until rows.size) {
                val cells = (0 until maxCols).map { col ->
                    cellValue(rows[i].getCell(col)).replace("|", "\\|")
                }
                sb.appendLine(cells.joinToString(" | ", "| ", " |"))
            }
            sb.appendLine()
        }

        workbook.close()
        return ConversionResult(markdown = sb.toString())
    }

    private fun cellValue(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                val v = cell.numericCellValue
                if (v == kotlin.math.floor(v) && !v.isInfinite()) v.toLong().toString()
                else formatter.formatCellValue(cell)
            }
            CellType.BLANK -> ""
            else -> formatter.formatCellValue(cell).trim()
        }
    }
}
