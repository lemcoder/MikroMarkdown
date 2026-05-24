package com.mikromarkdown

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue

class MarkItDownIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `convert HTML file end to end`() {
        val html = "<html><head><title>Test</title></head><body><h1>Title</h1><p>Content paragraph.</p></body></html>"
        val file = tempDir.resolve("test.html").toFile()
        file.writeText(html)

        val result = MarkItDown().convert(file)
        assertTrue(result.markdown.contains("# Title"), "Expected heading in:\n${result.markdown}")
        assertTrue(result.markdown.contains("Content paragraph"))
    }

    @Test
    fun `convert CSV file end to end`() {
        val csv = "Name,Score\nAlice,100\nBob,90"
        val file = tempDir.resolve("data.csv").toFile()
        file.writeText(csv)

        val result = MarkItDown().convert(file)
        assertTrue(result.markdown.contains("| Name | Score |"))
        assertTrue(result.markdown.contains("| Alice | 100 |"))
    }

    @Test
    fun `convert stream with StreamInfo`() {
        val text = "Hello from stream"
        val info = StreamInfo(extension = "txt")

        val result = MarkItDown().convert(text.byteInputStream(), info)
        assertTrue(result.markdown.contains("Hello from stream"))
    }

    @Test
    fun `output is normalized - no trailing whitespace`() {
        val html = "<html><body><p>Test   </p></body></html>"
        val file = tempDir.resolve("test.html").toFile()
        file.writeText(html)

        val result = MarkItDown().convert(file)
        result.markdown.lines().forEach { line ->
            assertTrue(line == line.trimEnd(), "Line has trailing whitespace: '$line'")
        }
    }
}
