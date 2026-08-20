package io.github.lemcoder.mikromarkdown

import com.goncalossilva.resources.Resource
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileIntegrationTest {
    private val mid = MikroMarkdown()

    private fun assertConversion(
        filename: String,
        mustInclude: List<String>,
        mustNotInclude: List<String> = emptyList(),
    ) {
        val ext = filename.substringAfterLast(".")
        val info = StreamInfo(extension = ext)
        val bytes = Resource("test_files/$filename").readBytes()
        val result = mid.convert(bytes, info)
        for (s in mustInclude) {
            assertTrue(result.markdown.contains(s), "Missing in $filename: '$s'")
        }
        for (s in mustNotInclude) {
            assertFalse(result.markdown.contains(s), "Should not appear in $filename: '$s'")
        }
    }

    /**
     * The office formats were removed rather than ported: they are editing formats, and a reader meets PDF and EPUB.
     * The fixtures stay for whenever an office plugin arrives, and this test pins the behaviour callers see until then.
     */
    @Test
    fun officeFormatsAreNotSupported() {
        for (filename in listOf("test.docx", "test.xlsx", "test.pptx")) {
            val bytes = Resource("test_files/$filename").readBytes()
            val info = StreamInfo(extension = filename.substringAfterLast("."))
            assertFailsWith<UnsupportedFormatException>(filename) { mid.convert(bytes, info) }
        }
    }

    @Test
    fun testBlogHtml() =
        assertConversion(
            filename = "test_blog.html",
            mustInclude =
                listOf(
                    "Large language models (LLMs) are powerful tools that can generate natural language texts",
                    "an example where high cost can easily prevent a generic complex",
                ),
        )

    @Test
    fun testWikipediaHtml() =
        assertConversion(
            filename = "test_wikipedia.html",
            mustInclude = listOf("Microsoft entered the operating system (OS) business in 1980"),
            mustNotInclude = listOf("move to sidebar"),
        )

    @Test
    fun testJson() =
        assertConversion(
            filename = "test.json",
            mustInclude =
                listOf(
                    "5b64c88c-b3c3-4510-bcb8-da0b200602d8",
                    "9700dc99-6685-40b4-9a3a-5e406dcb37f3",
                ),
        )

    @Test
    fun testEpub() =
        assertConversion(
            filename = "test.epub",
            mustInclude =
                listOf(
                    "A test EPUB document for MarkItDown testing",
                    "Chapter 1",
                    "Chapter 2",
                ),
        )
}
