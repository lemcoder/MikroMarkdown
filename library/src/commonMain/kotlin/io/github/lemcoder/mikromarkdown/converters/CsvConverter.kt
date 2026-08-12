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

    /**
     * Splits the text into records without building a field at a time.
     *
     * Each field is a range in the decoded text, so an ordinary field costs one substring and nothing else — no
     * per-character builder, no separate trim. Only fields containing escaped quotes, which cannot be a slice of the
     * input, fall back to assembling a string.
     */
    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = ArrayList<String>(EXPECTED_COLUMNS)

        var fieldStart = 0
        var quoted = false
        var quoteEscaped = false
        var index = 0

        fun field(end: Int): String {
            val raw = slice(text, fieldStart, end, quoted, quoteEscaped)
            quoted = false
            quoteEscaped = false
            return raw
        }

        fun endRecord(end: Int) {
            record.add(field(end))
            if (record.size > 1 || record[0].isNotEmpty()) records += record
            record = ArrayList(EXPECTED_COLUMNS)
        }

        var inQuotes = false
        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' ->
                    if (text.getOrNull(index + 1) == '"') {
                        quoteEscaped = true
                        index++
                    } else {
                        inQuotes = false
                    }

                inQuotes -> Unit
                char == '"' -> {
                    inQuotes = true
                    quoted = true
                }

                char == ',' -> {
                    record.add(field(index))
                    fieldStart = index + 1
                }

                char == '\n' || char == '\r' -> {
                    endRecord(index)
                    // Swallow the second half of a CRLF pair.
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                    fieldStart = index + 1
                }
            }
            index++
        }
        if (fieldStart < text.length || record.isNotEmpty()) endRecord(text.length)

        return records
    }

    /** The field between [start] and [end], unquoted and trimmed, copied only once. */
    private fun slice(text: String, start: Int, end: Int, quoted: Boolean, quoteEscaped: Boolean): String {
        var from = start
        var to = end
        while (from < to && text[from].isWhitespace()) from++
        while (to > from && text[to - 1].isWhitespace()) to--
        if (from >= to) return ""

        if (quoted) {
            if (text[from] == '"') from++
            if (to > from && text[to - 1] == '"') to--
            if (quoteEscaped) return text.substring(from, to).replace("\"\"", "\"")
        }
        return text.substring(from, to)
    }

    private companion object {
        const val EXPECTED_COLUMNS = 8
    }
}
