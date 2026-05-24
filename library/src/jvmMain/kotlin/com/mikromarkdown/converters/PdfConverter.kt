package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val bytes = stream.readBytes()
        val doc = Loader.loadPDF(bytes)
        val text = try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
        return ConversionResult(markdown = text)
    }
}
