package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.model.Asset
import io.github.lemcoder.mikromarkdown.model.PngEncoder

/** One image found on a page, already in a form something can display. */
internal class PageImage(val asset: Asset, val width: Int, val height: Int)

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
): PageImage? {
    if (width <= 0 || height <= 0) return null

    if (jpeg != null && jpeg.isNotEmpty()) {
        return PageImage(Asset(id = id, mediaType = "image/jpeg", bytes = jpeg, name = "$id.jpg"), width, height)
    }

    val rgba = pixels.asRgba(width, height) ?: return null
    val png = PngEncoder.encode(width, height, rgba) ?: return null
    return PageImage(Asset(id = id, mediaType = "image/png", bytes = png, name = "$id.png"), width, height)
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

private const val OPAQUE = 0xFF.toByte()

/** pdfium's object types; only images matter here. */
internal const val OBJECT_TYPE_IMAGE: Int = 3

/** The filter a JPEG stream carries, and the one case where the stream is already a file. */
internal const val JPEG_FILTER: String = "DCTDecode"
