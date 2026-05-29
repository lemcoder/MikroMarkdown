package io.github.lemcoder.mikromarkdown

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

class PythonComparisonTest {

    @Test
    fun testDocx() = compare("test.docx")

    @Test
    fun testXlsx() = compare("test.xlsx")

    @Test
    fun testPptx() = compare("test.pptx")

    @Test
    fun testEpub() = compare("test.epub")

    @Test
    fun testJson() = compare("test.json")

    @Test
    fun testBlogHtml() = compare("test_blog.html")

    @Test
    fun testWikipediaHtml() = compare("test_wikipedia.html")

    private fun compare(filename: String) {
        val url = javaClass.classLoader.getResource("test_files/$filename")
            ?: error("Resource not found: test_files/$filename")
        val file = File(url.toURI())

        val pythonOutput = runMarkitdown(file.absolutePath)
        val kotlinOutput = mid.convert(file.absolutePath).markdown

        val similarity = tokenSimilarity(pythonOutput, kotlinOutput)
        assertTrue(
            similarity >= 1,
            "Token similarity ${"%.0f".format(similarity * 100)}% < 100% for $filename\n" +
                "Python tokens missing from Kotlin: ${missingTokens(pythonOutput, kotlinOutput).take(20)}",
        )
    }

    // Normalize unicode characters to ASCII equivalents so both converters compare fairly
    private fun normalize(text: String): String =
        text
            .replace("–", "-")  // en dash
            .replace("—", "-")  // em dash
            .replace(" ", " ")  // non-breaking space
            .replace(Regex("%([0-9A-Fa-f]{2})")) { mr ->
                val cp = mr.groupValues[1].toInt(16)
                if (cp in 0x20..0x7E) cp.toChar().toString() else mr.value
            }
            // Strip interactive UI chrome (button labels) that Kotlin strips but Python keeps
            .replace(Regex("(?m)^.*move to sidebar.*$"), "")
            .replace(Regex("(?m)^\\s*hide\\s*$"), "")
            .replace(Regex("(?m)^[*\\s]*Toggle\\b.*$"), "")

    // Tokens longer than 3 chars, lowercased, deduped
    private fun tokenize(text: String): Set<String> =
        normalize(text).lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length > 3 }
            .toSet()

    private fun tokenSimilarity(reference: String, actual: String): Double {
        val refTokens = tokenize(reference)
        if (refTokens.isEmpty()) return 1.0
        return refTokens.intersect(tokenize(actual)).size.toDouble() / refTokens.size
    }

    private fun missingTokens(reference: String, actual: String): List<String> {
        val actualTokens = tokenize(actual)
        return tokenize(reference).filter { it !in actualTokens }.sorted()
    }

    companion object {
        private lateinit var mid: MikroMarkdown
        private lateinit var markitdownCmd: List<String>

        @BeforeAll
        @JvmStatic
        fun setup() {
            markitdownCmd = resolveMarkitdownCmd()
            assumeTrue(
                markitdownCmd.isNotEmpty(),
                "markitdown CLI not available — install via `pip install markitdown` or `uv tool install markitdown`",
            )
            mid = MarkItDown()
        }

        private fun resolveMarkitdownCmd(): List<String> {
            val candidates = listOf(
                listOf("markitdown"),
                listOf("python", "-m", "markitdown"),
                listOf("python3", "-m", "markitdown"),
                listOf("uvx", "markitdown[all]"),
                listOf("uvx", "markitdown"),
            )
            return candidates.firstOrNull { cmd ->
                try {
                    val process = ProcessBuilder(cmd + "--help")
                        .redirectErrorStream(true)
                        .start()
                    val finished = process.waitFor(10, TimeUnit.SECONDS)
                    if (!finished) { process.destroyForcibly(); return@firstOrNull false }
                    process.exitValue() == 0
                } catch (_: Exception) {
                    false
                }
            } ?: emptyList()
        }

        fun runMarkitdown(path: String): String {
            val process = ProcessBuilder(markitdownCmd + path)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            assumeTrue(finished, "markitdown timed out on $path")
            assumeTrue(process.exitValue() == 0, "markitdown failed on $path: ${process.errorStream.bufferedReader().readText()}")
            return stdout
        }
    }
}
