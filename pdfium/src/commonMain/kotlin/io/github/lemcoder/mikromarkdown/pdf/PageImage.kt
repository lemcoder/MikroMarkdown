package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.model.Asset

/**
 * One image found on a page, already in a form something can display.
 *
 * [top] is the upper edge of its box in page coordinates, where y grows upward, and is what decides where the figure
 * sits among the paragraphs.
 */
internal class PageImage(val asset: Asset, val width: Int, val height: Int, val top: Float = Float.MAX_VALUE)

/**
 * An image pdfium found on a page, turned into a file.
 *
 * Two shapes come back and they need opposite treatment. A photograph is stored as JPEG and the stream *is* the file,
 * so it is handed on untouched — re-encoding it would cost quality for nothing. Anything else is raw pixels with no
 * container: pdfium applies the filters and returns a bare buffer, which is why [PngEncoder] exists.
 *
 * The pixel format is not reported anywhere a JVM caller can read — `FPDFImageObj_GetImageMetadata` writes into a
 * struct, and the generated bridge passes that as a raw address the JVM cannot allocate. The buffer's own size gives
 * the same answer: bytes over width times height is the channel count.
 */
internal fun imageAsset(
    pixels: ByteArray,
    width: Int,
    height: Int,
    jpeg: ByteArray?,
    id: String,
    top: Float = Float.MAX_VALUE,
): PageImage? {
    if (width <= 0 || height <= 0) return null

    if (jpeg != null && jpeg.isNotEmpty()) {
        return PageImage(Asset(id, "image/jpeg", jpeg, "$id.jpg"), width, height, top)
    }

    val rgba = pixels.asRgba(width, height) ?: return null
    val png = PngEncoder.encode(width, height, rgba) ?: return null
    return PageImage(Asset(id, "image/png", png, "$id.png"), width, height, top)
}

/**
 * An image pdfium has already composited with its mask: RGBA, packed, ready to encode.
 *
 * Always PNG. The alpha is the whole point of taking this path, and JPEG has none.
 */
internal fun maskedImageAsset(rgba: ByteArray, width: Int, height: Int, id: String, top: Float): PageImage? {
    if (width <= 0 || height <= 0 || rgba.size != width * height * 4) return null
    // Compositing is rasterised at the size the page shows, not the size the image is stored at, so
    // it costs resolution on a photograph placed small. Worth it only where there is a mask to apply.
    if (!rgba.hasTransparency()) return null
    val png = PngEncoder.encode(width, height, rgba) ?: return null
    return PageImage(Asset(id, "image/png", png, "$id.png"), width, height, top)
}

/**
 * Widens whatever pdfium returned to the RGBA the encoder takes.
 *
 * A PDF image is grey, RGB or CMYK depending on its colour space, and the decoded buffer is packed with no padding, so
 * the channel count is the only thing left to tell them apart. Four channels mean CMYK rather than RGBA: transparency
 * in a PDF is a separate soft mask, never a fourth channel in the image's own data. A buffer that divides into none of
 * the three is something this does not understand — a 1-bit stencil mask, most likely — and is skipped rather than
 * guessed at.
 */
private fun ByteArray.asRgba(width: Int, height: Int): ByteArray? {
    val pixelCount = width * height
    if (pixelCount <= 0) return null

    return when (size / pixelCount) {
        1 -> ByteArray(pixelCount * 4) { at -> if (at % 4 == 3) OPAQUE else this[at / 4] }
        3 -> ByteArray(pixelCount * 4) { at -> if (at % 4 == 3) OPAQUE else this[at / 4 * 3 + at % 4] }
        4 -> cmykToRgba(pixelCount)
        else -> null
    }
}

/**
 * The naive conversion, which is the right amount of effort here.
 *
 * Going from CMYK to RGB properly needs the output profile the document was made for, and a reader showing a figure on
 * a phone has neither that nor a use for it. This is what every viewer does when it has no profile either.
 */
private fun ByteArray.cmykToRgba(pixelCount: Int): ByteArray {
    val out = ByteArray(pixelCount * 4)
    for (pixel in 0 until pixelCount) {
        val source = pixel * 4
        val black = this[source + 3].toInt() and 0xFF
        for (channel in 0..2) {
            val ink = (this[source + channel].toInt() and 0xFF) + black
            out[pixel * 4 + channel] = (255 - ink.coerceAtMost(255)).toByte()
        }
        out[pixel * 4 + 3] = OPAQUE
    }
    return out
}

/** Whether any pixel is less than fully opaque, which is what makes compositing worth its cost. */
private fun ByteArray.hasTransparency(): Boolean {
    var index = 3
    while (index < size) {
        if (this[index] != OPAQUE) return true
        index += 4
    }
    return false
}

private const val OPAQUE = 0xFF.toByte()

/** pdfium's object types; only images matter, and forms because images hide inside them. */
internal const val OBJECT_TYPE_IMAGE: Int = 3

/**
 * A form XObject: a nested content stream, and where a figure's raster usually lives.
 *
 * A page rarely draws an image directly. Anything placed by a figure environment — matplotlib, TikZ, an embedded PDF —
 * arrives as a form wrapping its own objects, so a walk that only reads the page's top level sees almost nothing. One
 * paper measured 229 images nested in forms against 1 at the top.
 */
internal const val OBJECT_TYPE_FORM: Int = 5

/** Forms nest; this stops a malformed file from recursing forever. */
internal const val MAX_FORM_DEPTH: Int = 8

/** The filter a JPEG stream carries, and the one case where the stream is already a file. */
internal const val JPEG_FILTER: String = "DCTDecode"
