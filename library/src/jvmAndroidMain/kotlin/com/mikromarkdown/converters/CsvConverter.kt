package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.InputStream
import java.io.InputStreamReader

class CsvConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension == "csv" || info.mimetype in setOf("text/csv", "application/csv")
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val reader = InputStreamReader(stream, Charsets.UTF_8)
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
