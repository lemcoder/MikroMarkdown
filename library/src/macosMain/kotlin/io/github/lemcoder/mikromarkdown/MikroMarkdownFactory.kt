package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.converters.CsvConverter
import io.github.lemcoder.mikromarkdown.converters.JsonConverter
import io.github.lemcoder.mikromarkdown.converters.MarkdownPassthroughConverter
import io.github.lemcoder.mikromarkdown.converters.PlainTextConverter
import io.github.lemcoder.mikromarkdown.converters.XmlConverter

/**
 * A [MikroMarkdown] with the converters that need no platform library.
 *
 * The document formats (DOCX, XLSX, PPTX, EPUB, PDF, HTML) still depend on JVM libraries and are absent here; this
 * target exists to measure what a native binary costs to start and run.
 */
public fun MikroMarkdown(): MikroMarkdown =
    MikroMarkdown(SignatureMimeDetector).apply {
        register(MarkdownPassthroughConverter())
        register(CsvConverter())
        register(JsonConverter())
        register(XmlConverter())
        register(PlainTextConverter(), priority = 10.0)
    }
