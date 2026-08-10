package io.github.lemcoder.mikromarkdown

import java.io.File
import org.junit.jupiter.api.Test

class DumpOutputTest {
    private val mid = MikroMarkdown()

    @Test
    fun dumpAll() {
        for (name in
            listOf(
                "test.docx",
                "test.xlsx",
                "test.pptx",
                "test.epub",
                "test.json",
                "test_blog.html",
                "test_wikipedia.html",
            )) {
            val url = javaClass.classLoader.getResource("test_files/$name") ?: continue
            val output = mid.convert(File(url.toURI()).absolutePath).markdown
            File("/tmp/kt_$name.md").writeText(output)
        }
    }
}
