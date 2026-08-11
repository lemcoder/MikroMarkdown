package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.RawBlock

public class MarkdownPassthroughConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("md", "markdown") || info.mimetype == "text/markdown"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = listOf(RawBlock(bytes.decodeToString())))
}
