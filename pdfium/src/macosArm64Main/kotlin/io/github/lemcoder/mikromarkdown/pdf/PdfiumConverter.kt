package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.plainTextBlocks
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import pdfium.FPDFText_ClosePage
import pdfium.FPDFText_CountChars
import pdfium.FPDFText_GetText
import pdfium.FPDFText_LoadPage
import pdfium.FPDF_CloseDocument
import pdfium.FPDF_ClosePage
import pdfium.FPDF_DestroyLibrary
import pdfium.FPDF_GetPageCount
import pdfium.FPDF_InitLibrary
import pdfium.FPDF_LoadMemDocument
import pdfium.FPDF_LoadPage

/**
 * PDF text through pdfium.
 *
 * Not registered by the library's factory: PDF costs a native library, so a caller asks for it.
 *
 * ```
 * val mikroMarkdown = MikroMarkdown().apply { register(PdfiumConverter()) }
 * ```
 */
public class PdfiumConverter : DocumentConverter {

    private companion object {
        const val UNMAPPED_GLYPH = '\uFFFE'
        val WORD = Regex("[\\p{L}]{2,}")
        val HYPHEN_BREAK = Regex("\\p{L}+\uFFFE\\p{L}+")
    }

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val text = StringBuilder()

        FPDF_InitLibrary()
        try {
            bytes.usePinned { pinned ->
                val document = FPDF_LoadMemDocument(pinned.addressOf(0), bytes.size, null) ?: return@usePinned
                try {
                    for (index in 0 until FPDF_GetPageCount(document)) {
                        val page = FPDF_LoadPage(document, index) ?: continue
                        val textPage = FPDFText_LoadPage(page)
                        if (textPage != null) {
                            text.append(pageText(textPage))
                            // A page break reads as a paragraph break to the text blocks.
                            text.append('\n')
                            FPDFText_ClosePage(textPage)
                        }
                        FPDF_ClosePage(page)
                    }
                } finally {
                    FPDF_CloseDocument(document)
                }
            }
        } finally {
            FPDF_DestroyLibrary()
        }

        return Document(blocks = plainTextBlocks(text.toString().joinHyphenatedWords()))
    }

    /**
     * pdfium emits U+FFFE — a permanent non-character — where a glyph has no Unicode mapping, which for a typeset
     * document is nearly always the hyphen at a line break.
     *
     * Dropping it always would fuse real compounds: "chat-optimized" became "chatoptimized". So the document decides.
     * If both halves appear elsewhere as words in their own right the hyphen was real and is restored; otherwise the
     * halves are two pieces of one broken word and are joined.
     */
    private fun String.joinHyphenatedWords(): String {
        if (indexOf(UNMAPPED_GLYPH) < 0) return this

        // The halves of a broken word must not vouch for themselves: "con" and "firming" are only
        // in the text because the break put them there, so every break is cut before counting.
        val unbroken = replace(HYPHEN_BREAK, " ")
        val vocabulary = WORD.findAll(unbroken).map { it.value.lowercase() }.toSet()
        val out = StringBuilder(length)
        for (index in indices) {
            val char = this[index]
            if (char != UNMAPPED_GLYPH) {
                out.append(char)
                continue
            }
            var wordStart = out.length
            while (wordStart > 0 && out[wordStart - 1].isLetter()) wordStart--
            val left = out.subSequence(wordStart, out.length).toString().lowercase()
            val right = substring(index + 1).takeWhile { it.isLetter() }.lowercase()
            if (isRealCompound(left, right, vocabulary)) out.append('-')
        }
        return out.toString()
    }

    /** A hyphen the author wrote, rather than one the typesetter added at a line break. */
    private fun isRealCompound(left: String, right: String, vocabulary: Set<String>): Boolean =
        left.isNotEmpty() && right.isNotEmpty() && left in vocabulary && right in vocabulary

    /** pdfium writes UTF-16 into a caller-supplied buffer and counts the terminating NUL. */
    @OptIn(ExperimentalForeignApi::class)
    private fun pageText(textPage: pdfium.FPDF_TEXTPAGE): String {
        val count = FPDFText_CountChars(textPage)
        if (count <= 0) return ""
        return memScoped {
            val buffer = allocArray<UShortVar>(count + 1)
            val written = FPDFText_GetText(textPage, 0, count, buffer)
            if (written <= 1) "" else CharArray(written - 1) { Char(buffer[it].toInt()) }.concatToString()
        }
    }
}
