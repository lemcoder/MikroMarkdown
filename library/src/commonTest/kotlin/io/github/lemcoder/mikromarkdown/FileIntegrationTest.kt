package io.github.lemcoder.mikromarkdown

import com.goncalossilva.resources.Resource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

expect fun testMarkItDown(): MikroMarkdown

class FileIntegrationTest {
    private val mid = testMarkItDown()

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

    @Test
    fun testDocx() = assertConversion(
        filename = "test.docx",
        mustInclude = listOf(
            "314b0a30-5b04-470b-b9f7-eed2c2bec74a",
            "49e168b7-d2ae-407f-a055-2167576f39a1",
            "# Abstract",
            "# Introduction",
            "AutoGen: Enabling Next-Gen LLM Applications via Multi-Agent Conversation",
        ),
    )

    @Test
    fun testXlsx() = assertConversion(
        filename = "test.xlsx",
        mustInclude = listOf(
            "09060124-b5e7-4717-9d07-3c046eb",
            "6ff4173b-42a5-4784-9b19-f49caff4d93d",
            "affc7dad-52dc-4b98-9b5d-51e65d8a8ad0",
        ),
    )

    @Test
    fun testPptx() = assertConversion(
        filename = "test.pptx",
        mustInclude = listOf(
            "2cdda5c8-e50e-4db4-b5f0-9722a649f455",
            "04191ea8-5c73-4215-a1d3-1cfb43aaaf12",
            "44bf7d06-5e7a-4a40-a2e1-a2e42ef28c8a",
            "1b92870d-e3b5-4e65-8153-919f4ff45592",
            "AutoGen: Enabling Next-Gen LLM Applications via Multi-Agent Conversation",
        ),
    )

    @Test
    fun testBlogHtml() = assertConversion(
        filename = "test_blog.html",
        mustInclude = listOf(
            "Large language models (LLMs) are powerful tools that can generate natural language texts for various applications",
            "an example where high cost can easily prevent a generic complex",
        ),
    )

    @Test
    fun testWikipediaHtml() = assertConversion(
        filename = "test_wikipedia.html",
        mustInclude = listOf(
            "Microsoft entered the operating system (OS) business in 1980",
        ),
        mustNotInclude = listOf(
            "move to sidebar",
        ),
    )

    @Test
    fun testJson() = assertConversion(
        filename = "test.json",
        mustInclude = listOf(
            "5b64c88c-b3c3-4510-bcb8-da0b200602d8",
            "9700dc99-6685-40b4-9a3a-5e406dcb37f3",
        ),
    )

    @Test
    fun testEpub() = assertConversion(
        filename = "test.epub",
        mustInclude = listOf(
            "A test EPUB document for MarkItDown testing",
            "Chapter 1",
            "Chapter 2",
        ),
    )
}
