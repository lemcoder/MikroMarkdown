package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell

/**
 * RFC 4180 CSV, parsed directly.
 *
 * The format is small enough that reading it by hand costs less than a dependency would, and the
 * same code then serves every target.
 */
public class CsvConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "csv" || info.mimetype in setOf("text/csv", "application/csv")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val records = parseRecords(bytes)
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
     * Splits the input into records over the raw bytes.
     *
     * Decoding the whole document first would allocate a UTF-16 copy of it — twice its size — before
     * a single field is read. The delimiters are all ASCII, and UTF-8 never encodes an ASCII byte as
     * part of a multi-byte character, so scanning bytes is safe and only the fields are decoded.
     */
    private fun parseRecords(bytes: ByteArray): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = ArrayList<String>(EXPECTED_COLUMNS)

        var fieldStart = 0
        var quoted = false
        var quoteEscaped = false
        var index = 0
        var inQuotes = false

        fun field(end: Int): String {
            val raw = decodeField(bytes, fieldStart, end, quoted, quoteEscaped)
            quoted = false
            quoteEscaped = false
            return raw
        }

        fun endRecord(end: Int) {
            record.add(field(end))
            if (record.size > 1 || record[0].isNotEmpty()) records += record
            record = ArrayList(EXPECTED_COLUMNS)
        }

        while (index < bytes.size) {
            when (val byte = bytes[index]) {
                QUOTE ->
                    if (inQuotes) {
                        if (index + 1 < bytes.size && bytes[index + 1] == QUOTE) {
                            quoteEscaped = true
                            index++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        inQuotes = true
                        quoted = true
                    }

                COMMA ->
                    if (!inQuotes) {
                        record.add(field(index))
                        fieldStart = index + 1
                    }

                NEWLINE,
                RETURN ->
                    if (!inQuotes) {
                        endRecord(index)
                        // Swallow the second half of a CRLF pair.
                        if (byte == RETURN && index + 1 < bytes.size && bytes[index + 1] == NEWLINE) index++
                        fieldStart = index + 1
                    }
            }
            index++
        }
        if (fieldStart < bytes.size || record.isNotEmpty()) endRecord(bytes.size)

        return records
    }

    /** The field between [start] and [end], unquoted and trimmed, decoded once. */
    private fun decodeField(bytes: ByteArray, start: Int, end: Int, quoted: Boolean, quoteEscaped: Boolean): String {
        var from = start
        var to = end
        while (from < to && bytes[from].isBlank()) from++
        while (to > from && bytes[to - 1].isBlank()) to--
        if (from >= to) return ""

        if (quoted) {
            if (bytes[from] == QUOTE) from++
            if (to > from && bytes[to - 1] == QUOTE) to--
            if (quoteEscaped) return bytes.decodeToString(from, to).replace("\"\"", "\"")
        }
        return bytes.decodeToString(from, to)
    }

    private fun Byte.isBlank(): Boolean = this == SPACE || this == TAB || this == NEWLINE || this == RETURN

    private companion object {
        const val EXPECTED_COLUMNS = 8
        const val QUOTE: Byte = '"'.code.toByte()
        const val COMMA: Byte = ','.code.toByte()
        const val NEWLINE: Byte = '\n'.code.toByte()
        const val RETURN: Byte = '\r'.code.toByte()
        const val SPACE: Byte = ' '.code.toByte()
        const val TAB: Byte = '\t'.code.toByte()
    }
}
