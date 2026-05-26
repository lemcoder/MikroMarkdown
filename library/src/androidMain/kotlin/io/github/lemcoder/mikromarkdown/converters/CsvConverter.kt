package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.InputStreamReader

class CsvConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "csv" || info.mimetype in setOf("text/csv", "application/csv")
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val reader = InputStreamReader(bytes.inputStream(), Charsets.UTF_8)
        val allRecords = CSVParser(reader, CSVFormat.DEFAULT.builder().setTrim(true).build()).records

        if (allRecords.isEmpty()) return ConversionResult(markdown = "")

        val headers = allRecords[0].toList()
        if (headers.isEmpty()) return ConversionResult(markdown = "")

        val sb = StringBuilder()
        sb.appendLine(headers.map { it.escapeCell() }.joinToString(" | ", "| ", " |"))
        sb.appendLine(headers.map { "---" }.joinToString(" | ", "| ", " |"))

        for (i in 1 until allRecords.size) {
            val cells = (0 until headers.size).map { col ->
                allRecords[i].get(col).escapeCell()
            }
            sb.appendLine(cells.joinToString(" | ", "| ", " |"))
        }

        return ConversionResult(markdown = sb.toString().trimEnd())
    }

    private fun String.escapeCell(): String = replace("|", "\\|").replace("\n", " ")
}
