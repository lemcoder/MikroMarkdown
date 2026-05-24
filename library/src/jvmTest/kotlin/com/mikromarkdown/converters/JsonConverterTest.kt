package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonConverterTest {
    private val converter = JsonConverter()
    private val info = StreamInfo(extension = "json", mimetype = "application/json")

    @Test
    fun `accepts json extension`() {
        assert(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `simple json converts to expected markdown`() {
        val input = resource("fixtures/json/simple.json")
        val expected = resource("fixtures/json/simple.expected.md")
        val result = converter.convert(input.openStream(), info)
        assertEquals(expected.readText().trim(), result.markdown.trim())
    }

    @Test
    fun `json output wrapped in fenced code block`() {
        val json = """{"key":"value"}"""
        val result = converter.convert(json.byteInputStream(), info)
        assertTrue(result.markdown.startsWith("```json"))
        assertTrue(result.markdown.endsWith("```"))
    }

    private fun resource(path: String) =
        javaClass.classLoader.getResource(path) ?: error("Resource not found: $path")
}
