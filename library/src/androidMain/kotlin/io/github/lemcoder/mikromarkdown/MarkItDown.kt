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

fun MarkItDown(context: Context? = null): MikroMarkdown = MikroMarkdown(AndroidMimeDetector).apply {
    register(MarkdownPassthroughConverter())
    register(HtmlConverter())
    register(CsvConverter())
    register(JsonConverter())
    register(XmlConverter())
    register(DocxConverter())
    register(XlsxConverter())
    register(PptxConverter())
    register(EpubConverter())
    if (context != null) {
        PDFBoxResourceLoader.init(context)
        register(PdfConverter())
    }
    register(PlainTextConverter(), priority = 10.0)
}

fun MikroMarkdown.convert(file: File): ConversionResult = convert(file.absolutePath)
