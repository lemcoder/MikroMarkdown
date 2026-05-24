package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlainTextConverterTest {
    private val converter = PlainTextConverter()
    private val info = StreamInfo(extension = "txt", mimetype = "text/plain")

    @Test
    fun `accepts txt extension`() {
        assert(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `simple text converts to expected markdown`() {
        val input = resource("fixtures/txt/simple.txt")
        val expected = resource("fixtures/txt/simple.expected.md")
        val result = converter.convert(input.openStream(), info)
        assertEquals(expected.readText().trim(), result.markdown.trim())
    }

    private fun resource(path: String) =
        javaClass.classLoader.getResource(path) ?: error("Resource not found: $path")
}
