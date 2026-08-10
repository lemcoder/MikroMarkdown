package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.RawBlock

class PlainTextConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("txt", "log", "text") ||
               info.mimetype == "text/plain"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = listOf(RawBlock(bytes.decodeToString())))
}
