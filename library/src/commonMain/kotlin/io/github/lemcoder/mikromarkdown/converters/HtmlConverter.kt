package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.utils.HtmlToDocument
import io.github.lemcoder.mikromarkdown.utils.dataUriAsset
import io.github.lemcoder.mikromarkdown.utils.withAssets

public class HtmlConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension in setOf("html", "htm") || info.mimetype in setOf("text/html", "application/xhtml+xml")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        var embedded = 0
        // A remote src stays a URL — it is a reference, and resolving it would mean fetching it. A
        // data: URI is already the bytes, and leaving it inline makes the model carry an image as a
        // multi-megabyte string that every consumer has to decode again.
        return HtmlToDocument.parse(bytes.decodeToString(), info.localPath.orEmpty()).withAssets { url ->
            dataUriAsset(url, id = "embedded-${embedded++}")
        }
    }
}
