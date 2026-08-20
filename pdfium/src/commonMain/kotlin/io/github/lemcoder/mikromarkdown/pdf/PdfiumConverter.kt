package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
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

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = plainTextBlocks(extractPages(bytes).restored()))
}

/** Every page, in order; [PageText] carries the text and the wraps pdfium collapsed. */
internal expect fun extractPages(bytes: ByteArray): List<PageText>
