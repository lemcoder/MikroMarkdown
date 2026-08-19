package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.converters.CsvConverter
import io.github.lemcoder.mikromarkdown.converters.EpubConverter
import io.github.lemcoder.mikromarkdown.converters.HtmlConverter
import io.github.lemcoder.mikromarkdown.converters.JsonConverter
import io.github.lemcoder.mikromarkdown.converters.MarkdownPassthroughConverter
import io.github.lemcoder.mikromarkdown.converters.PlainTextConverter
import io.github.lemcoder.mikromarkdown.converters.XmlConverter

/**
 * A [MikroMarkdown] with the converters that need no platform library.
 *
 * PDF is the only format still missing here; everything else the library converts is shared.
 */
public fun MikroMarkdown(): MikroMarkdown =
    MikroMarkdown(SignatureMimeDetector).apply {
        register(MarkdownPassthroughConverter())
        register(HtmlConverter())
        register(EpubConverter())
        register(CsvConverter())
        register(JsonConverter())
        register(XmlConverter())
        register(PlainTextConverter(), priority = 10.0)
    }
