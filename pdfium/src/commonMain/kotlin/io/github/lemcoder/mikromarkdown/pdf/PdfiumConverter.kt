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
 * same pdfium, and everything above [extractText] is shared. Turning that text into paragraphs, including the
 * de-hyphenation that pdfium's unmapped glyphs call for, is `plainTextBlocks`' job rather than a second copy here.
 */
public class PdfiumConverter : DocumentConverter {

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = plainTextBlocks(extractText(bytes)))
}

/** Every page's text, concatenated, one page per line. */
internal expect fun extractText(bytes: ByteArray): String
