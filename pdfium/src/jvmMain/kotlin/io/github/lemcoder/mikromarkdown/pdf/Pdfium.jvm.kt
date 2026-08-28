package io.github.lemcoder.mikromarkdown.pdf

import java.io.File
import pdfium.FPDFText_ClosePage
import pdfium.FPDFText_CountChars
import pdfium.FPDFText_GetCharBox
import pdfium.FPDFText_GetText
import pdfium.FPDFText_IsHyphen
import pdfium.FPDFText_LoadPage
import pdfium.FPDF_CloseDocument
import pdfium.FPDF_ClosePage
import pdfium.FPDF_GetPageCount
import pdfium.FPDF_InitLibrary
import pdfium.FPDF_LoadDocument
import pdfium.FPDF_LoadPage

/**
 * pdfium is initialised once per process and never destroyed.
 *
 * `FPDF_InitLibrary` and `FPDF_DestroyLibrary` are not the matching pair they look like: pairing them per call makes
 * extraction differ between one process and the next, where initialising once gives every process the same answer.
 */
private val pdfiumLibrary: Lazy<Unit> = lazy { FPDF_InitLibrary() }

/**
 * The JVM half, over the JNI bridges the Konan plugin generates from the same `.def` cinterop binds.
 *
 * The bindings carry the names of the C functions they call, so this reads as pdfium's own API and needs no wrapper
 * layer in between.
 *
 * The bytes reach pdfium as a file rather than as a buffer. `FPDF_LoadMemDocument` keeps the caller's pointer and reads
 * through it for as long as the document is open, but the generated bridge pairs `GetByteArrayElements` with
 * `ReleaseByteArrayElements` and lets go before it returns, so every page load after that reads memory the JVM has
 * taken back. It mostly works, which is the worst way to fail: the first conversion in a process reads an untouched
 * region and is correct, later ones come back four generated spaces short and `AutoGen uses` reads as `AutoGenuses`.
 * Zeroing the array under an open document drops extraction to nothing, which is what proved it. `FPDF_LoadDocument`
 * owns everything it reads. The native leg needs none of this: `usePinned` holds the array for the document's life.
 */
internal actual fun extractPages(bytes: ByteArray): List<PageText> {
    val pages = mutableListOf<PageText>()

    pdfiumLibrary.value
    // The file has to outlive the document: pdfium reads it lazily, the same way it would read a buffer.
    val file = File.createTempFile("mikromarkdown", ".pdf")
    try {
        file.writeBytes(bytes)
        val document = FPDF_LoadDocument(file.absolutePath, null)
        if (document == 0L) return emptyList()
        try {
            for (index in 0 until FPDF_GetPageCount(document)) {
                val page = FPDF_LoadPage(document, index)
                if (page == 0L) continue
                val textPage = FPDFText_LoadPage(page)
                if (textPage != 0L) {
                    pages += pageText(textPage)
                    FPDFText_ClosePage(textPage)
                }
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    } finally {
        file.delete()
    }

    return pages
}

/** pdfium writes UTF-16 into a caller-supplied buffer and counts the terminating NUL. */
private fun pageText(textPage: Long): PageText {
    val count = FPDFText_CountChars(textPage)
    if (count <= 0) return PageText("", IntArray(0))
    val buffer = ShortArray(count + 1)
    val written = FPDFText_GetText(textPage, 0, count, buffer)
    if (written <= 1) return PageText("", IntArray(0))
    // One character in, one character out, so a text index is a pdfium character index.
    val text = CharArray(written - 1) { Char(buffer[it].toInt() and 0xFFFF) }.concatToString()
    return PageText(text, hyphenWraps(textPage, text))
}

/**
 * The collapsed wraps, as [PageText.hyphenWraps] describes them.
 *
 * `FPDFText_IsHyphen` marks every hyphen pdfium removed, and the character box is what proves the line ended there
 * rather than the marker standing for something else on the same line.
 */
private fun hyphenWraps(textPage: Long, text: String): IntArray {
    val wraps = mutableListOf<Int>()
    for (index in text.indices) {
        if (
            text[index] == HYPHEN_MARKER && FPDFText_IsHyphen(textPage, index) != 0 && startsLowerLine(textPage, index)
        ) {
            wraps += index
        }
    }
    return wraps.toIntArray()
}

/** Whether the character after [index] sits below it — which is what makes [index] the end of a line. */
private fun startsLowerLine(textPage: Long, index: Int): Boolean {
    val left = DoubleArray(1)
    val right = DoubleArray(1)
    val bottom = DoubleArray(1)
    val top = DoubleArray(1)

    if (FPDFText_GetCharBox(textPage, index, left, right, bottom, top) == 0) return false
    val hyphenBottom = bottom[0]
    if (FPDFText_GetCharBox(textPage, index + 1, left, right, bottom, top) == 0) return false
    return top[0] < hyphenBottom
}
