package io.github.lemcoder.mikromarkdown.pdf

import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
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
import pdfium.FPDF_LoadMemDocument
import pdfium.FPDF_LoadPage

/**
 * pdfium is initialised once per process and never destroyed.
 *
 * `FPDF_InitLibrary` and `FPDF_DestroyLibrary` are not the matching pair they look like. Pairing them per call makes
 * extraction differ between one process and the next; initialising once gives every process the same sequence, which is
 * the only thing this buys.
 *
 * It does not make extraction repeatable. pdfium carries state across document loads that the first conversion in a
 * process does not see, and from the second conversion on the AutoGen fixture comes back four generated spaces short —
 * `AutoGen uses` reads as `AutoGenuses`. Destroying the library between conversions does not reset it. Unfixed, and
 * below this line rather than in it.
 */
@OptIn(ExperimentalForeignApi::class) private val pdfiumLibrary: Lazy<Unit> = lazy { FPDF_InitLibrary() }

/** The native half, over the cinterop bindings. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun extractPages(bytes: ByteArray): List<PageText> {
    val pages = mutableListOf<PageText>()

    pdfiumLibrary.value
    bytes.usePinned { pinned ->
        val document = FPDF_LoadMemDocument(pinned.addressOf(0), bytes.size, null) ?: return@usePinned
        try {
            for (index in 0 until FPDF_GetPageCount(document)) {
                val page = FPDF_LoadPage(document, index) ?: continue
                val textPage = FPDFText_LoadPage(page)
                if (textPage != null) {
                    pages += pageText(textPage)
                    FPDFText_ClosePage(textPage)
                }
                FPDF_ClosePage(page)
            }
        } finally {
            FPDF_CloseDocument(document)
        }
    }

    return pages
}

/** pdfium writes UTF-16 into a caller-supplied buffer and counts the terminating NUL. */
@OptIn(ExperimentalForeignApi::class)
private fun pageText(textPage: pdfium.FPDF_TEXTPAGE): PageText {
    val count = FPDFText_CountChars(textPage)
    if (count <= 0) return PageText("", IntArray(0))
    val text = memScoped {
        val buffer = allocArray<UShortVar>(count + 1)
        val written = FPDFText_GetText(textPage, 0, count, buffer)
        // One character in, one character out, so a text index is a pdfium character index.
        if (written <= 1) "" else CharArray(written - 1) { Char(buffer[it].toInt()) }.concatToString()
    }
    if (text.isEmpty()) return PageText("", IntArray(0))
    return PageText(text, hyphenWraps(textPage, text))
}

/**
 * The collapsed wraps, as [PageText.hyphenWraps] describes them.
 *
 * `FPDFText_IsHyphen` marks every hyphen pdfium removed, and the character box is what proves the line ended there
 * rather than the marker standing for something else on the same line.
 */
@OptIn(ExperimentalForeignApi::class)
private fun hyphenWraps(textPage: pdfium.FPDF_TEXTPAGE, text: String): IntArray {
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
@OptIn(ExperimentalForeignApi::class)
private fun startsLowerLine(textPage: pdfium.FPDF_TEXTPAGE, index: Int): Boolean = memScoped {
    val left = alloc<DoubleVar>()
    val right = alloc<DoubleVar>()
    val bottom = alloc<DoubleVar>()
    val top = alloc<DoubleVar>()

    if (FPDFText_GetCharBox(textPage, index, left.ptr, right.ptr, bottom.ptr, top.ptr) == 0) return false
    val hyphenBottom = bottom.value
    if (FPDFText_GetCharBox(textPage, index + 1, left.ptr, right.ptr, bottom.ptr, top.ptr) == 0) return false
    top.value < hyphenBottom
}
