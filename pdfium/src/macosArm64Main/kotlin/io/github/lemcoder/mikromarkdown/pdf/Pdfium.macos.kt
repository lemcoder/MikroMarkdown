package io.github.lemcoder.mikromarkdown.pdf

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
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
import pdfium.FPDFText_GetCharBox
import pdfium.FPDFText_GetText
import pdfium.FPDFText_IsHyphen
import pdfium.FPDFText_LoadPage
import pdfium.FPDF_CloseDocument
import pdfium.FPDF_ClosePage
import pdfium.FPDF_GetPageCount
import pdfium.FPDF_GetPageHeightF
import pdfium.FPDF_GetPageWidthF
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
                    pages +=
                        pageText(textPage)
                            .copyWith(pageImages(page, index, figureFilter(page, textPage)), charTops(textPage))
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

/**
 * The images on a page, each as a file something can display.
 *
 * The same rules as the JVM leg, which is where they are written down; cinterop can allocate the buffers directly
 * rather than going through arrays, so only the plumbing differs.
 */
@OptIn(ExperimentalForeignApi::class)
private fun pageImages(page: pdfium.FPDF_PAGE, pageIndex: Int, filter: FigureFilter): List<PageImage> {
    val out = mutableListOf<PageImage>()
    collectImages(
        filter,
        FPDFPage_CountObjects(page),
        { FPDFPage_GetObject(page, it) },
        "page${pageIndex + 1}",
        out,
        0,
    )
    return out
}

/** Walks a container's objects, descending into forms, collecting whatever is an image. */
@OptIn(ExperimentalForeignApi::class)
private fun collectImages(
    filter: FigureFilter,
    count: Int,
    objectAt: (Int) -> pdfium.FPDF_PAGEOBJECT?,
    path: String,
    out: MutableList<PageImage>,
    depth: Int,
) {
    for (index in 0 until count) {
        val obj = objectAt(index) ?: continue
        when (FPDFPageObj_GetType(obj)) {
            OBJECT_TYPE_IMAGE -> obj.asImage(filter, "$path-image${index + 1}")?.let { out += it }
            OBJECT_TYPE_FORM ->
                if (depth < MAX_FORM_DEPTH) {
                    collectImages(
                        filter,
                        FPDFFormObj_CountObjects(obj),
                        { FPDFFormObj_GetObject(obj, it.toULong()) },
                        "$path-form${index + 1}",
                        out,
                        depth + 1,
                    )
                }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun pdfium.FPDF_PAGEOBJECT.asImage(filter: FigureFilter, id: String): PageImage? {
    // Measured before anything is read: an OCR'd scan has hundreds of fragments, and none is worth decoding.
    if (!isFigureIn(filter)) return null

    val size =
        memScoped {
            val width = alloc<UIntVar>()
            val height = alloc<UIntVar>()
            if (FPDFImageObj_GetImagePixelSize(this@asImage, width.ptr, height.ptr) == 0) null
            else width.value.toInt() to height.value.toInt()
        } ?: return null

    val jpeg = if (hasJpegFilter()) readInto(::FPDFImageObj_GetImageDataRaw) else null
    val pixels = if (jpeg == null) readInto(::FPDFImageObj_GetImageDataDecoded) else null
    return imageAsset(pixels ?: ByteArray(0), size.first, size.second, jpeg, id, top())
}

/** The page's size and whether it carries text, which is what decides the scan behind that text. */
@OptIn(ExperimentalForeignApi::class)
private fun figureFilter(page: pdfium.FPDF_PAGE, textPage: pdfium.FPDF_TEXTPAGE): FigureFilter =
    FigureFilter(FPDF_GetPageWidthF(page), FPDF_GetPageHeightF(page), FPDFText_CountChars(textPage) > 0)

/** Whether [filter] counts the object's box as a figure's. */
@OptIn(ExperimentalForeignApi::class)
private fun pdfium.FPDF_PAGEOBJECT.isFigureIn(filter: FigureFilter): Boolean = memScoped {
    val left = alloc<FloatVar>()
    val bottom = alloc<FloatVar>()
    val right = alloc<FloatVar>()
    val top = alloc<FloatVar>()
    if (FPDFPageObj_GetBounds(this@isFigureIn, left.ptr, bottom.ptr, right.ptr, top.ptr) == 0) true
    else filter.keeps(left.value, bottom.value, right.value, top.value)
}

/** The upper edge of the object's box, in page coordinates. */
@OptIn(ExperimentalForeignApi::class)
private fun pdfium.FPDF_PAGEOBJECT.top(): Float = memScoped {
    val left = alloc<FloatVar>()
    val bottom = alloc<FloatVar>()
    val right = alloc<FloatVar>()
    val top = alloc<FloatVar>()
    if (FPDFPageObj_GetBounds(this@top, left.ptr, bottom.ptr, right.ptr, top.ptr) == 0) Float.MAX_VALUE else top.value
}

/** Every character's upper edge, read once for the page. */
@OptIn(ExperimentalForeignApi::class)
private fun charTops(textPage: pdfium.FPDF_TEXTPAGE): FloatArray = memScoped {
    val count = FPDFText_CountChars(textPage)
    val tops = FloatArray(count)
    val left = alloc<DoubleVar>()
    val right = alloc<DoubleVar>()
    val bottom = alloc<DoubleVar>()
    val top = alloc<DoubleVar>()
    for (index in 0 until count) {
        tops[index] =
            if (FPDFText_GetCharBox(textPage, index, left.ptr, right.ptr, bottom.ptr, top.ptr) == 0) Float.MAX_VALUE
            else top.value.toFloat()
    }
    tops
}

/** pdfium's two-call buffer protocol: ask for the length, allocate, ask again. */
@OptIn(ExperimentalForeignApi::class)
private fun pdfium.FPDF_PAGEOBJECT.readInto(
    read: (pdfium.FPDF_PAGEOBJECT?, kotlinx.cinterop.CValuesRef<*>?, ULong) -> ULong
): ByteArray? {
    val length = read(this, null, 0uL)
    if (length == 0uL || length > MAX_IMAGE_BYTES) return null
    return memScoped {
        val buffer = allocArray<ByteVar>(length.toInt())
        if (read(this@readInto, buffer, length) == 0uL) null else buffer.readBytes(length.toInt())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun pdfium.FPDF_PAGEOBJECT.hasJpegFilter(): Boolean =
    (0 until FPDFImageObj_GetImageFilterCount(this)).any { filter ->
        val length = FPDFImageObj_GetImageFilter(this, filter, null, 0uL)
        if (length == 0uL) return@any false
        memScoped {
            val name = allocArray<ByteVar>(length.toInt())
            FPDFImageObj_GetImageFilter(this@hasJpegFilter, filter, name, length)
            name.toKString() == JPEG_FILTER
        }
    }

/** A guard against a malformed stream claiming a size that would exhaust the heap. */
private const val MAX_IMAGE_BYTES: ULong = 67_108_864uL
