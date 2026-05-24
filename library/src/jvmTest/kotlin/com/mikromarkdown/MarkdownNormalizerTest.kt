package com.mikromarkdown

import com.mikromarkdown.utils.MarkdownNormalizer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MarkdownNormalizerTest {

    @Test
    fun `trailing whitespace stripped`() {
        val result = MarkdownNormalizer.normalize("hello   \nworld  ")
        assertEquals("hello\nworld", result)
    }

    @Test
    fun `three or more blank lines collapsed to two`() {
        val result = MarkdownNormalizer.normalize("a\n\n\n\nb")
        assertEquals("a\n\nb", result)
    }

    @Test
    fun `two blank lines preserved`() {
        val result = MarkdownNormalizer.normalize("a\n\nb")
        assertEquals("a\n\nb", result)
    }

    @Test
    fun `leading and trailing whitespace trimmed`() {
        val result = MarkdownNormalizer.normalize("\n\nhello\n\n")
        assertEquals("hello", result)
    }
}
