package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.utils.TikaMimeDetector
import io.github.lemcoder.mikromarkdown.converters.CsvConverter
import io.github.lemcoder.mikromarkdown.converters.DocxConverter
import io.github.lemcoder.mikromarkdown.converters.EpubConverter
import io.github.lemcoder.mikromarkdown.converters.HtmlConverter
import io.github.lemcoder.mikromarkdown.converters.JsonConverter
import io.github.lemcoder.mikromarkdown.converters.MarkdownPassthroughConverter
import io.github.lemcoder.mikromarkdown.converters.PdfConverter
import io.github.lemcoder.mikromarkdown.converters.PlainTextConverter
import io.github.lemcoder.mikromarkdown.converters.PptxConverter
import io.github.lemcoder.mikromarkdown.converters.XlsxConverter
import io.github.lemcoder.mikromarkdown.converters.XmlConverter
import java.io.File

fun MarkItDown(): MarkItDown = MarkItDown(
    detectMime = TikaMimeDetector::detect,
    converters = listOf(
        MarkdownPassthroughConverter() to 0.0,
        HtmlConverter() to 0.0,
        CsvConverter() to 0.0,
        JsonConverter() to 0.0,
        XmlConverter() to 0.0,
        DocxConverter() to 0.0,
        XlsxConverter() to 0.0,
        PptxConverter() to 0.0,
        EpubConverter() to 0.0,
        PdfConverter() to 0.0,
        PlainTextConverter() to 10.0,
    ),
)

fun MarkItDown.convert(file: File): ConversionResult = convert(file.absolutePath)
