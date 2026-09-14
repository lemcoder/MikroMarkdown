package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.plainTextBlocks

/**
 * PDF text through pdfium.
 *
 * Not registered by the library's factory: PDF costs a native library, so a caller asks for it.
 *
 * ```
 * val mikroMarkdown = MikroMarkdown().apply { register(PdfiumConverter()) }
 * ```
 *
 * The extraction itself is per-platform — cinterop on native, generated JNI bridges on the JVM — but both reach the
 * same pdfium, and everything above [extractPages] is shared: each leg reports what pdfium saw, and one copy of the
 * rules here turns it into a document. Deciding what a wrapped hyphen meant belongs to `plainTextBlocks`, which reads
 * the whole document rather than one page.
 */
public class PdfiumConverter : DocumentConverter {

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val pages = extractPages(bytes)
        val images = pages.flatMap { it.images }
        // The images follow the text rather than sitting where they appear. A page object carries no
        // reading order, so placing a figure among the paragraphs means reading the content stream
        // and tracking where each `Do` falls — worth doing, and a good deal more than this.
        val figures = images.map { Paragraph(listOf(Image(alt = "", url = "", assetId = it.asset.id))) }
        return Document(
            blocks = plainTextBlocks(pages.restored()) + figures,
            assets = images.map { it.asset },
        )
    }
}

/** Every page, in order; [PageText] carries the text and the wraps pdfium collapsed. */
internal expect fun extractPages(bytes: ByteArray): List<PageText>
