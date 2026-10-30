package io.github.lemcoder.mikromarkdown.pdf

import java.io.File
import pdfium.FPDF_InitLibrary
import pdfium.FPDF_GetPageCount
import pdfium.FPDF_GetPageHeightF
import pdfium.FPDF_GetPageWidthF
import pdfium.FPDF_LoadPage
import pdfium.FPDF_ClosePage
import pdfium.FPDF_CloseDocument
import pdfium.FPDF_LoadDocument
import pdfium.FPDFText_LoadPage
import pdfium.FPDFFormObj_CountObjects
import pdfium.FPDFFormObj_GetObject
import pdfium.FPDFImageObj_GetImageDataDecoded
import pdfium.FPDFImageObj_GetImageDataRaw
import pdfium.FPDFImageObj_GetImageFilter
import pdfium.FPDFImageObj_GetImageFilterCount
import pdfium.FPDFImageObj_GetImagePixelSize
import pdfium.FPDFPageObj_GetBounds
import pdfium.FPDFPageObj_GetType
import pdfium.FPDFPage_CountObjects
import pdfium.FPDFPage_GetObject
import pdfium.FPDFText_ClosePage
import pdfium.FPDFText_CountChars
import pdfium.FPDFText_IsHyphen
import pdfium.FPDFText_GetCharBox
import pdfium.FPDFText_GetText
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * pdfium is initialised once per process and never destroyed.
 *
 * `FPDF_InitLibrary` and `FPDF_DestroyLibrary` are not the matching pair they look like: pairing them per call makes
 * extraction differ between one process and the next, where initialising once gives every process the same answer.
 */
private val pdfiumLibrary: Lazy<Unit> = lazy { FPDF_InitLibrary() }

/**
 * The Android half, over the JNI bridges the Konan plugin generates from the same `.def` cinterop binds.
 *
 * The bindings carry the names of the C functions they call, so this reads as pdfium's own API and needs no wrapper
 * layer in between.
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
        val document = FPDF_LoadDocument(file.absolutePath, null)
        if (document == 0L) return emptyList()
        try {
            for (index in 0 until FPDF_GetPageCount(document)) {
                val page = FPDF_LoadPage(document, index)
                if (page == 0L) continue
                val textPage = FPDFText_LoadPage(page)
                if (textPage != 0L) {
                    val images = pageImages(PageScope(document, page, textPage), index)
                    pages += pageText(textPage).copyWith(images, charTops(textPage))
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
        if (text[index] != HYPHEN_MARKER) continue
        if (FPDFText_IsHyphen(textPage, index) != 0 && startsLowerLine(textPage, index)) wraps += index
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

/**
 * The images on a page, each as a file something can display.
 *
 * pdfium reports the pixel size and the decoded bytes through buffers a JVM caller can allocate,
 * which is the only reason this is possible on this leg at all: the bitmap and metadata calls hand
 * back raw addresses instead, and the generated bridge passes those as a `Long` the JVM cannot read.
 */
private fun pageImages(scope: PageScope, pageIndex: Int): List<PageImage> {
    val out = mutableListOf<PageImage>()
    val path = "page${pageIndex + 1}"
    collectImages(scope, FPDFPage_CountObjects(scope.page), { FPDFPage_GetObject(scope.page, it) }, path, out, 0)
    return out
}

/** Walks a container's objects, descending into forms, collecting whatever is an image. */
private fun collectImages(
    scope: PageScope,
    count: Int,
    objectAt: (Int) -> Long,
    path: String,
    out: MutableList<PageImage>,
    depth: Int,
) {
    for (index in 0 until count) {
        val obj = objectAt(index)
        if (obj == 0L) continue
        when (FPDFPageObj_GetType(obj)) {
            OBJECT_TYPE_IMAGE -> obj.asImage(scope, "$path-image${index + 1}")?.let { out += it }
            OBJECT_TYPE_FORM ->
                if (depth < MAX_FORM_DEPTH) {
                    collectImages(
                        scope,
                        FPDFFormObj_CountObjects(obj),
                        { FPDFFormObj_GetObject(obj, it.toLong()) },
                        "$path-form${index + 1}",
                        out,
                        depth + 1,
                    )
                }
        }
    }
}

/**
 * pdfium's own compositing, reached through a hand-written stub.
 *
 * Declared as an object because JNI resolves the symbol from the class it is declared in, and
 * `Java_io_github_lemcoder_mikromarkdown_pdf_Masks_renderedRgba` is what native/masks.c exports. The
 * library it lives in is the one the generated bindings already loaded.
 */
private object Masks {
    external fun renderedRgba(document: Long, page: Long, imageObject: Long, size: IntArray): ByteArray?
}

/**
 * The composited bytes, or null if this build cannot produce them.
 *
 * The stub is built by CMake beside the generated bridges, and a stale one — the library rebuilt
 * without the hand-written source — has the bridges but not this symbol. Losing the mask is a worse
 * picture; losing the document is a crash, so the raw stream is used instead.
 */
private fun compositedRgba(document: Long, page: Long, imageObject: Long, size: IntArray): ByteArray? =
    try {
        Masks.renderedRgba(document, page, imageObject, size)
    } catch (_: UnsatisfiedLinkError) {
        null
    }

private fun Long.asImage(scope: PageScope, id: String): PageImage? {
    // Measured before anything is read: an OCR'd scan has hundreds of fragments, and none is worth decoding.
    if (!isFigureIn(scope.filter)) return null

    // Composited first: it is the only form that carries the soft mask, so a figure meant to sit on
    // the page transparently does not arrive on a black rectangle.
    val size = IntArray(2)
    compositedRgba(scope.document, scope.page, this, size)?.let { rgba ->
        maskedImageAsset(rgba, size[0], size[1], id, top())?.let { return it }
    }

    val width = IntArray(1)
    val height = IntArray(1)
    if (FPDFImageObj_GetImagePixelSize(this, width, height) == 0) return null

    // A JPEG stream is already a file; anything else is pixels that need a container.
    val jpeg = if (hasJpegFilter()) readInto(::FPDFImageObj_GetImageDataRaw) else null
    val pixels = if (jpeg == null) readInto(::FPDFImageObj_GetImageDataDecoded) else null
    return imageAsset(pixels ?: ByteArray(0), width[0], height[0], jpeg, id, top())
}

/** Whether [filter] counts the object's box as a figure's. */
private fun Long.isFigureIn(filter: FigureFilter): Boolean {
    val left = FloatArray(1)
    val bottom = FloatArray(1)
    val right = FloatArray(1)
    val top = FloatArray(1)
    if (FPDFPageObj_GetBounds(this, left, bottom, right, top) == 0) return true
    return filter.keeps(left[0], bottom[0], right[0], top[0])
}

/** The open document and page an image is read from, and the rule that decides whether it is a figure. */
private class PageScope(val document: Long, val page: Long, textPage: Long) {
    // The page's size and whether it carries text, which is what decides the scan behind that text.
    val filter = FigureFilter(FPDF_GetPageWidthF(page), FPDF_GetPageHeightF(page), FPDFText_CountChars(textPage) > 0)
}

/** The upper edge of the object's box, in page coordinates. */
private fun Long.top(): Float {
    val left = FloatArray(1)
    val bottom = FloatArray(1)
    val right = FloatArray(1)
    val top = FloatArray(1)
    return if (FPDFPageObj_GetBounds(this, left, bottom, right, top) == 0) Float.MAX_VALUE else top[0]
}

/**
 * Every character's upper edge, read once for the page.
 *
 * Asking per figure would be the same call repeated for each of them; a paper's page can carry
 * thirty images over two thousand characters.
 */
private fun charTops(textPage: Long): FloatArray {
    val count = FPDFText_CountChars(textPage)
    val tops = FloatArray(count)
    val left = DoubleArray(1)
    val right = DoubleArray(1)
    val bottom = DoubleArray(1)
    val top = DoubleArray(1)
    for (index in 0 until count) {
        tops[index] =
            if (FPDFText_GetCharBox(textPage, index, left, right, bottom, top) == 0) Float.MAX_VALUE
            else top[0].toFloat()
    }
    return tops
}

/**
 * pdfium's two-call buffer protocol: ask for the length, allocate, ask again.
 *
 * A `void*` crosses JNI as a direct buffer's address, so pdfium writes into native memory and the bytes are copied out.
 */
private fun Long.readInto(read: (Long, Buffer?, Long) -> Long): ByteArray? {
    val length = read(this, null, 0L)
    if (length <= 0L || length > MAX_IMAGE_BYTES) return null
    val buffer = ByteBuffer.allocateDirect(length.toInt())
    if (read(this, buffer, length) == 0L) return null
    return ByteArray(length.toInt()).also { buffer.get(it) }
}

private fun Long.hasJpegFilter(): Boolean =
    (0 until FPDFImageObj_GetImageFilterCount(this)).any { filter ->
        val length = FPDFImageObj_GetImageFilter(this, filter, null, 0L)
        if (length <= 0L) return@any false
        val buffer = ByteBuffer.allocateDirect(length.toInt())
        FPDFImageObj_GetImageFilter(this, filter, buffer, length)
        // The buffer is a NUL-terminated C string.
        ByteArray(length.toInt()).also { buffer.get(it) }.decodeToString().trimEnd('\u0000') == JPEG_FILTER
    }

/** A guard against a malformed stream claiming a size that would exhaust the heap. */
private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
