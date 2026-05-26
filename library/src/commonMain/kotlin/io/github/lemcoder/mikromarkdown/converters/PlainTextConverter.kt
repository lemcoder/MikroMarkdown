package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo

class PlainTextConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("txt", "log", "text") ||
               info.mimetype == "text/plain"
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        return ConversionResult(markdown = bytes.decodeToString())
    }
}
