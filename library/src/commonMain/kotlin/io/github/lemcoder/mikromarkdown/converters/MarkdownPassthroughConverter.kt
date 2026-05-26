package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo

class MarkdownPassthroughConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("md", "markdown") || info.mimetype == "text/markdown"
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        return ConversionResult(markdown = bytes.decodeToString())
    }
}
