package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Block
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
        val pages = extractPages(bytes).map { it.inReadingOrder() }
        return Document(blocks = pages.blocks(), assets = pages.flatMap { page -> page.images.map { it.asset } })
    }
}

/**
 * The document's blocks, with each figure among the paragraphs it was printed between.
 *
 * A page is cut at the character its figures sit above, so the text is built in the pieces the figures divide it into
 * rather than as one run with the pictures swept to the end. Splitting the text does cost the joining across a cut — a
 * paragraph interrupted by a figure becomes two — which is what the page looks like anyway.
 */
private fun List<PageText>.blocks(): List<Block> {
    val hyphen = hyphenWritten()
    return flatMap { page ->
        buildList {
            var cursor = 0
            for (image in page.images.sortedBy { page.placementOf(it) }) {
                val cut = page.placementOf(image).coerceIn(cursor, page.text.length)
                addAll(plainTextBlocks(page.restored(hyphen, cursor, cut)))
                add(Paragraph(listOf(Image(alt = "", url = "", assetId = image.asset.id))))
                cursor = cut
            }
            addAll(plainTextBlocks(page.restored(hyphen, cursor, page.text.length)))
        }
    }
}

/** Every page, in order; [PageText] carries the text, the wraps pdfium collapsed, and the figures. */
internal expect fun extractPages(bytes: ByteArray): List<PageText>
