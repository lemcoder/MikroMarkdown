package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MarkdownPassthroughConverterTest {
    private val converter = MarkdownPassthroughConverter()
    private val info = StreamInfo(extension = "md")

    @Test
    fun `accepts md extension`() {
        assert(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `markdown passes through unchanged`() {
        val input = resource("fixtures/md/simple.md")
        val expected = resource("fixtures/md/simple.expected.md")
        val result = converter.convert(input.openStream(), info)
        assertEquals(expected.readText().trim(), result.markdown.trim())
    }

    private fun resource(path: String) =
        javaClass.classLoader.getResource(path) ?: error("Resource not found: $path")
}
