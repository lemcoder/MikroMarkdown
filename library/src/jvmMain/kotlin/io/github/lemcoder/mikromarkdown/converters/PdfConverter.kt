package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.utils.plainTextBlocks
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

public class PdfConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val doc = Loader.loadPDF(bytes)
        try {
            val title = doc.documentInformation?.title?.trim()?.ifBlank { null }
            // Paragraph markers let the text blocks split on real paragraph breaks
            // instead of collapsing a page into one block.
            val stripper =
                PDFTextStripper().apply {
                    sortByPosition = true
                    setAddMoreFormatting(true)
                    paragraphStart = "\n"
                }
            return Document(blocks = plainTextBlocks(stripper.getText(doc)), title = title)
        } finally {
            doc.close()
        }
    }
}
