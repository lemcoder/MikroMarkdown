package io.github.lemcoder.mikromarkdown.utils

object MarkdownNormalizer {
    fun normalize(text: String): String {
        val stripped = text.lines().joinToString("\n") { it.trimEnd() }
        return stripped.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
