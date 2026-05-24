package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvConverterTest {
    private val converter = CsvConverter()
    private val info = StreamInfo(extension = "csv", mimetype = "text/csv")

    @Test
    fun `accepts csv extension`() {
        assert(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `simple csv converts to expected markdown`() {
        val input = resource("fixtures/csv/simple.csv")
        val expected = resource("fixtures/csv/simple.expected.md")
        val result = converter.convert(input.openStream(), info)
        assertEquals(expected.readText().trim(), result.markdown.trim())
    }

    @Test
    fun `csv output contains GFM table structure`() {
        val csv = "Name,Age\nAlice,30\nBob,25"
        val result = converter.convert(csv.byteInputStream(), info)
        assertTrue(result.markdown.contains("| Name | Age |"))
        assertTrue(result.markdown.contains("| --- | --- |"))
        assertTrue(result.markdown.contains("| Alice | 30 |"))
    }

    private fun resource(path: String) =
        javaClass.classLoader.getResource(path) ?: error("Resource not found: $path")
}
