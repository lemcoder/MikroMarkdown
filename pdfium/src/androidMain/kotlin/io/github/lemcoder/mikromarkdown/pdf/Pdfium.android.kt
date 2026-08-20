package io.github.lemcoder.mikromarkdown.pdf

import java.io.File
import pdfium.kniBridge1
import pdfium.kniBridge15
import pdfium.kniBridge16
import pdfium.kniBridge26
import pdfium.kniBridge27
import pdfium.kniBridge4
import pdfium.kniBridge52
import pdfium.kniBridge53
import pdfium.kniBridge54
import pdfium.kniBridge58
import pdfium.kniBridge66
import pdfium.kniBridge71

/**
 * pdfium is initialised once per process and never destroyed.
 *
 * `FPDF_InitLibrary` and `FPDF_DestroyLibrary` are not the matching pair they look like: pairing them per call makes
 * extraction differ between one process and the next, where initialising once gives every process the same answer.
 */
private val pdfiumLibrary: Lazy<Unit> = lazy { initLibrary() }

/**
 * The Android half, over the JNI bridges the Konan plugin generates from the same `.def` cinterop binds.
 *
 * The bridges are numbered rather than named — that is what a runtime-free binding looks like — so each is wrapped here
 * with the name from its doc comment, and nothing else in the module sees them.
 *
 * The bytes reach pdfium as a file rather than as a buffer. `FPDF_LoadMemDocument` keeps the caller's pointer and
 * reads through it for as long as the document is open, but the generated bridge pairs `GetByteArrayElements` with
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
        val document = loadDocument(file.absolutePath, null)
        if (document == 0L) return emptyList()
        try {
            for (index in 0 until pageCount(document)) {
                val page = loadPage(document, index)
                if (page == 0L) continue
                val textPage = loadTextPage(page)
                if (textPage != 0L) {
                    pages += pageText(textPage)
                    closeTextPage(textPage)
                }
                closePage(page)
            }
        } finally {
            closeDocument(document)
        }
    } finally {
        file.delete()
    }

    return pages
}

/** pdfium writes UTF-16 into a caller-supplied buffer and counts the terminating NUL. */
private fun pageText(textPage: Long): PageText {
    val count = charCount(textPage)
    if (count <= 0) return PageText("", IntArray(0))
    val buffer = ShortArray(count + 1)
    val written = readText(textPage, 0, count, buffer)
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
        if (text[index] == HYPHEN_MARKER && isHyphen(textPage, index) != 0 && startsLowerLine(textPage, index)) {
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

    if (charBox(textPage, index, left, right, bottom, top) == 0) return false
    val hyphenBottom = bottom[0]
    if (charBox(textPage, index + 1, left, right, bottom, top) == 0) return false
    return top[0] < hyphenBottom
}

private fun initLibrary() = kniBridge1()

private fun loadDocument(path: String, password: String?): Long = kniBridge4(path, password)

private fun pageCount(document: Long): Int = kniBridge15(document)

private fun loadPage(document: Long, index: Int): Long = kniBridge16(document, index)

private fun closePage(page: Long) = kniBridge26(page)

private fun closeDocument(document: Long) = kniBridge27(document)

private fun loadTextPage(page: Long): Long = kniBridge52(page)

private fun closeTextPage(textPage: Long) = kniBridge53(textPage)

private fun charCount(textPage: Long): Int = kniBridge54(textPage)

private fun isHyphen(textPage: Long, index: Int): Int = kniBridge58(textPage, index)

private fun charBox(
    textPage: Long,
    index: Int,
    left: DoubleArray,
    right: DoubleArray,
    bottom: DoubleArray,
    top: DoubleArray,
): Int = kniBridge66(textPage, index, left, right, bottom, top)

private fun readText(textPage: Long, start: Int, count: Int, buffer: ShortArray): Int =
    kniBridge71(textPage, start, count, buffer)
