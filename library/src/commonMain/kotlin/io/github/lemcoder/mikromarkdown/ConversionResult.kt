package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.model.Document

data class ConversionResult(
    val markdown: String,
    val title: String? = null,
    /** The intermediate model the Markdown was rendered from. */
    val document: Document = Document(),
)
