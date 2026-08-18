package io.github.lemcoder.mikromarkdown.pdf

import pdfium.kniBridge1
import pdfium.kniBridge15
import pdfium.kniBridge16
import pdfium.kniBridge2
import pdfium.kniBridge26
import pdfium.kniBridge27
import pdfium.kniBridge5
import pdfium.kniBridge52
import pdfium.kniBridge53
import pdfium.kniBridge54
import pdfium.kniBridge71

/**
 * The JVM half, over the JNI bridges the Konan plugin generates from the same `.def` cinterop binds.
 *
 * The bridges are numbered rather than named — that is what a runtime-free binding looks like — so each is wrapped here
 * with the name from its doc comment, and nothing else in the module sees them.
 */
internal actual fun extractText(bytes: ByteArray): String {
    val text = StringBuilder()

    initLibrary()
    try {
        val document = loadDocument(bytes, null)
        if (document == 0L) return ""
        try {
            for (index in 0 until pageCount(document)) {
                val page = loadPage(document, index)
                if (page == 0L) continue
                val textPage = loadTextPage(page)
                if (textPage != 0L) {
                    text.append(pageText(textPage))
                    text.append('\n')
                    closeTextPage(textPage)
                }
                closePage(page)
            }
        } finally {
            closeDocument(document)
        }
    } finally {
        destroyLibrary()
    }

    return text.toString()
}

/** pdfium writes UTF-16 into a caller-supplied buffer and counts the terminating NUL. */
private fun pageText(textPage: Long): String {
    val count = charCount(textPage)
    if (count <= 0) return ""
    val buffer = ShortArray(count + 1)
    val written = readText(textPage, 0, count, buffer)
    return if (written <= 1) "" else CharArray(written - 1) { Char(buffer[it].toInt() and 0xFFFF) }.concatToString()
}

private fun initLibrary() = kniBridge1()

private fun destroyLibrary() = kniBridge2()

private fun loadDocument(bytes: ByteArray, password: String?): Long = kniBridge5(bytes, bytes.size, password)

private fun pageCount(document: Long): Int = kniBridge15(document)

private fun loadPage(document: Long, index: Int): Long = kniBridge16(document, index)

private fun closePage(page: Long) = kniBridge26(page)

private fun closeDocument(document: Long) = kniBridge27(document)

private fun loadTextPage(page: Long): Long = kniBridge52(page)

private fun closeTextPage(textPage: Long) = kniBridge53(textPage)

private fun charCount(textPage: Long): Int = kniBridge54(textPage)

private fun readText(textPage: Long, start: Int, count: Int, buffer: ShortArray): Int =
    kniBridge71(textPage, start, count, buffer)
