package io.github.lemcoder.mikromarkdown.pdf

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

/** The native half, over the cinterop bindings. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun extractText(bytes: ByteArray): String {
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

    return text.toString()
}

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
