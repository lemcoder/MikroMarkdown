package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.PDDocument

class PdfConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val doc = PDDocument.load(bytes)
        val text = try {
            PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
        return ConversionResult(markdown = text)
    }
}
