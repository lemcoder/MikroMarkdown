package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.InputStreamReader

class CsvConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "csv" || info.mimetype in setOf("text/csv", "application/csv")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val reader = InputStreamReader(bytes.inputStream(), Charsets.UTF_8)
        val records = CSVParser(reader, CSVFormat.DEFAULT.builder().setTrim(true).build()).records
        if (records.isEmpty()) return Document()

        val header = records[0].toList()
        if (header.isEmpty()) return Document()

        val rows = records.drop(1).map { record ->
            // Ragged rows are padded by the renderer; only extra columns need trimming here.
            List(header.size) { col -> TableCell(record.takeIf { col < it.size() }?.get(col) ?: "") }
        }

        return Document(blocks = listOf(Table(header = header.map { TableCell(it) }, rows = rows)))
    }
}
