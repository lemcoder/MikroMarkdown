package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.utils.HtmlToMarkdown

class HtmlConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("html", "htm") ||
               info.mimetype in setOf("text/html", "application/xhtml+xml")
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val html = bytes.toString(Charsets.UTF_8)
        val (markdown, title) = HtmlToMarkdown.convert(html)
        return ConversionResult(markdown = markdown, title = title)
    }
}
