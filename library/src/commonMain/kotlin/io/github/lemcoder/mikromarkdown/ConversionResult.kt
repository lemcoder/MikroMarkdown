package io.github.lemcoder.mikromarkdown

data class ConversionResult(
    val markdown: String,
    val title: String? = null,
)
