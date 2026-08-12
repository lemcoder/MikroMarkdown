package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell

/**
 * RFC 4180 CSV, parsed directly.
 *
 * The JVM build uses commons-csv; on native there is no such library, and the format is small enough that reading it by
 * hand costs less than a dependency would.
 */
public class CsvConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "csv" || info.mimetype in setOf("text/csv", "application/csv")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val records = parseRecords(bytes.decodeToString())
        if (records.isEmpty()) return Document()

        val header = records.first()
        if (header.isEmpty()) return Document()

        val rows =
            records.drop(1).map { record ->
                // Ragged rows are padded by the renderer; only extra columns need trimming here.
                List(header.size) { column -> TableCell(record.getOrElse(column) { "" }) }
            }

        return Document(blocks = listOf(Table(header = header.map { TableCell(it) }, rows = rows)))
    }

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun endField() {
            record += field.toString().trim()
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            // A trailing newline must not produce a phantom record.
            if (record.size > 1 || record.first().isNotEmpty()) records += record
            record = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' ->
                    // A doubled quote inside a quoted field is a literal quote.
                    if (text.getOrNull(index + 1) == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                    }

                quoted -> field.append(char)
                char == '"' -> quoted = true
                char == ',' -> endField()
                char == '\r' -> if (text.getOrNull(index + 1) == '\n') Unit else endRecord()
                char == '\n' -> endRecord()
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()

        return records
    }
}
