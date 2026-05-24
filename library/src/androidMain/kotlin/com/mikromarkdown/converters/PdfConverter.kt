package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import com.tom.roush.pdfbox.pdmodel.PDDocument
import com.tom.roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val bytes = stream.readBytes()
        val doc = PDDocument.load(bytes)
        val text = try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
        return ConversionResult(markdown = text)
    }
}
