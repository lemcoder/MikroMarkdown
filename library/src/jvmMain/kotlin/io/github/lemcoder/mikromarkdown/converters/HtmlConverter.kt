package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.utils.HtmlToDocument

class HtmlConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("html", "htm") ||
               info.mimetype in setOf("text/html", "application/xhtml+xml")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        HtmlToDocument.parse(bytes.toString(Charsets.UTF_8), info.localPath.orEmpty())
}
