package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import com.mikromarkdown.utils.HtmlToMarkdown
import java.io.InputStream

class HtmlConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension in setOf("html", "htm") ||
               info.mimetype in setOf("text/html", "application/xhtml+xml")
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val html = stream.readBytes().toString(Charsets.UTF_8)
        val (markdown, title) = HtmlToMarkdown.convert(html)
        return ConversionResult(markdown = markdown, title = title)
    }
}
