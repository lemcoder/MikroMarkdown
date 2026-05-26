package io.github.lemcoder.mikromarkdown

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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
import io.github.lemcoder.mikromarkdown.utils.AndroidMimeDetector
import java.io.File

fun MarkItDown(context: Context? = null): MarkItDown = MarkItDown(
    detectMime = AndroidMimeDetector::detect,
    converters = buildList {
        add(MarkdownPassthroughConverter() to 0.0)
        add(HtmlConverter() to 0.0)
        add(CsvConverter() to 0.0)
        add(JsonConverter() to 0.0)
        add(XmlConverter() to 0.0)
        add(DocxConverter() to 0.0)
        add(XlsxConverter() to 0.0)
        add(PptxConverter() to 0.0)
        add(EpubConverter() to 0.0)
        if (context != null) {
            PDFBoxResourceLoader.init(context)
            add(PdfConverter() to 0.0)
        }
        add(PlainTextConverter() to 10.0)
    },
)

fun MarkItDown.convert(file: File): ConversionResult = convert(file.absolutePath)
