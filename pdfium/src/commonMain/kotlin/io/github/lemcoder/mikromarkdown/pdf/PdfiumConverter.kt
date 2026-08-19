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
 * same pdfium, and everything above [extractText] is shared.
 */
public class PdfiumConverter : DocumentConverter {

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "pdf" || info.mimetype == "application/pdf"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = plainTextBlocks(extractText(bytes).joinHyphenatedWords()))

    /**
     * pdfium emits U+FFFE where a glyph has no Unicode mapping, which in a typeset document is nearly always the hyphen
     * at a line break.
     *
     * Dropping it always would fuse real compounds — "chat-optimized" became "chatoptimized" — so the document decides:
     * if both halves appear elsewhere as words in their own right the hyphen was the author's and is restored,
     * otherwise the halves are one broken word and are joined. The halves themselves are cut from the vocabulary first,
     * since they are only in the text because the break put them there.
     *
     * This is a heuristic standing in for geometry. A hyphenation hyphen ends a line and a compound hyphen does not,
     * which `FPDFText_GetCharBox` would answer outright.
     */
    private fun String.joinHyphenatedWords(): String {
        if (indexOf(UNMAPPED_GLYPH) < 0) return this

        val vocabulary = WORD.findAll(replace(HYPHEN_BREAK, " ")).map { it.value.lowercase() }.toSet()
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

    private companion object {
        const val UNMAPPED_GLYPH = '\uFFFE'
        val WORD = Regex("[\\p{L}]{2,}")
        val HYPHEN_BREAK = Regex("\\p{L}+\uFFFE\\p{L}+")
    }
}

/** Every page's text, concatenated, one page per line. */
internal expect fun extractText(bytes: ByteArray): String
