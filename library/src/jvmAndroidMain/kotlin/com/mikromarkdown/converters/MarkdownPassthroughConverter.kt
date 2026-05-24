package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import java.io.InputStream

class MarkdownPassthroughConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension in setOf("md", "markdown") || info.mimetype == "text/markdown"
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val text = stream.readBytes().toString(Charsets.UTF_8)
        return ConversionResult(markdown = text)
    }
}
