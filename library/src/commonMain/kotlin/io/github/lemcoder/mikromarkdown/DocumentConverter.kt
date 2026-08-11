package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.model.Document

/**
 * Parses one input format into the shared [Document] model.
 *
 * Converters never produce Markdown — rendering is [io.github.lemcoder.mikromarkdown.render.MarkdownRenderer]'s job.
 */
public interface DocumentConverter {
    public fun accepts(bytes: ByteArray, info: StreamInfo): Boolean

    public fun parse(bytes: ByteArray, info: StreamInfo): Document
}
