package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlConverterTest {
    private val converter = XmlConverter()
    private val info = StreamInfo(extension = "xml", mimetype = "application/xml")

    @Test
    fun `accepts xml extension`() {
        assert(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `simple xml converts to expected markdown`() {
        val input = resource("fixtures/xml/simple.xml")
        val expected = resource("fixtures/xml/simple.expected.md")
        val result = converter.convert(input.openStream(), info)
        assertEquals(expected.readText().trim(), result.markdown.trim())
    }

    @Test
    fun `xml output wrapped in fenced code block`() {
        val xml = "<root><item>value</item></root>"
        val result = converter.convert(xml.byteInputStream(), info)
        assertTrue(result.markdown.startsWith("```xml"))
        assertTrue(result.markdown.endsWith("```"))
    }

    private fun resource(path: String) =
        javaClass.classLoader.getResource(path) ?: error("Resource not found: $path")
}
