package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.converters.CsvConverter
import io.github.lemcoder.mikromarkdown.converters.EpubConverter
import io.github.lemcoder.mikromarkdown.converters.HtmlConverter
import io.github.lemcoder.mikromarkdown.converters.JsonConverter
import io.github.lemcoder.mikromarkdown.converters.MarkdownPassthroughConverter
import io.github.lemcoder.mikromarkdown.converters.PlainTextConverter
import io.github.lemcoder.mikromarkdown.converters.XmlConverter

/**
 * A [MikroMarkdown] with every converter the library ships.
 *
 * One list for every target, because every converter is now common. PDF is the exception and is not here: it costs a
 * native library, so the `:pdfium` module provides it and the caller opts in with `register(PdfiumConverter())`.
 */
public fun MikroMarkdown(): MikroMarkdown =
    MikroMarkdown(SignatureMimeDetector).apply {
        register(MarkdownPassthroughConverter())
        register(HtmlConverter())
        register(CsvConverter())
        register(JsonConverter())
        register(XmlConverter())
        register(EpubConverter())
        register(PlainTextConverter(), priority = 10.0)
    }
