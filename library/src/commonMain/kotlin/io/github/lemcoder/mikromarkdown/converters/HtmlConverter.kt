package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.utils.HtmlToDocument

public class HtmlConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("html", "htm") || info.mimetype in setOf("text/html", "application/xhtml+xml")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        HtmlToDocument.parse(bytes.decodeToString(), info.localPath.orEmpty())
}
