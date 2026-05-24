package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HtmlConverterTest {
    private val converter = HtmlConverter()
    private val info = StreamInfo(extension = "html", mimetype = "text/html")

    @Test
    fun `accepts html extension`() {
        assert(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `simple html converts to expected markdown`() {
        val input = resource("fixtures/html/simple.html")
        val expected = resource("fixtures/html/simple.expected.md")
        val result = converter.convert(input.openStream(), info)
        assertEquals(expected.readText().trim(), result.markdown.trim())
    }

    @Test
    fun `extracts title from html`() {
        val html = "<html><head><title>My Title</title></head><body><p>text</p></body></html>"
        val result = converter.convert(html.byteInputStream(), info)
        assertNotNull(result.title)
        assertEquals("My Title", result.title)
    }

    private fun resource(path: String) =
        javaClass.classLoader.getResource(path) ?: error("Resource not found: $path")
}
