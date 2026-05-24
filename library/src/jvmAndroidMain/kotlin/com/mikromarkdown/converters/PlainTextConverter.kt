package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import java.io.InputStream

class PlainTextConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension in setOf("txt", "log", "text") ||
               info.mimetype == "text/plain"
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val charset = info.charset ?: "UTF-8"
        val text = stream.readBytes().toString(charset(charset))
        return ConversionResult(markdown = text)
    }
}
