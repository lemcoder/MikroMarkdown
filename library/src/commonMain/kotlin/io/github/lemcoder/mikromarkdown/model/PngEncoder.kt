package io.github.lemcoder.mikromarkdown.model

import korlibs.io.compression.compress
import korlibs.io.compression.deflate.ZLib

/**
 * Writes 8-bit RGBA pixels as a PNG.
 *
 * Needed because most images inside a PDF are not image files: a JPEG stream can be handed on as it stands, but a
 * Flate-compressed bitmap is raw pixels with no container, and every target needs one to produce. Nothing in the Kotlin
 * ecosystem encodes PNG on Kotlin/Native, and the format's essentials are small — a signature, three chunks, a CRC
 * apiece, and the deflate that korlibs-compression already provides.
 *
 * Filtering is left at "none", which trades a larger file for a much simpler encoder. A screenshot inside a PDF
 * compresses well enough on deflate alone.
 */
public object PngEncoder {

    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    private const val BIT_DEPTH = 8
    private const val COLOR_TYPE_RGBA = 6
    private const val CHANNELS = 4

    /**
     * @param pixels RGBA, four bytes per pixel, row-major, [width] * [height] * 4 bytes long.
     * @return the PNG file, or null if the dimensions and the buffer disagree.
     */
    public fun encode(width: Int, height: Int, pixels: ByteArray): ByteArray? {
        if (width <= 0 || height <= 0) return null
        if (pixels.size != width * height * CHANNELS) return null

        val header = ByteArray(13)
        header.writeInt(0, width)
        header.writeInt(4, height)
        header[8] = BIT_DEPTH.toByte()
        header[9] = COLOR_TYPE_RGBA.toByte()
        // Compression 0 (deflate), filter 0 (adaptive), interlace 0 (none) — the only values PNG has.

        val chunks =
            listOf(
                SIGNATURE,
                chunk("IHDR", header),
                chunk("IDAT", scanlines(width, height, pixels).compress(ZLib)),
                chunk("IEND", ByteArray(0)),
            )

        // Concatenated by hand: a list of boxed bytes would cost more than the encoding does.
        val out = ByteArray(chunks.sumOf { it.size })
        var at = 0
        for (part in chunks) {
            part.copyInto(out, at)
            at += part.size
        }
        return out
    }

    /** Each row is preceded by its filter byte; 0 means the row is stored as it is. */
    private fun scanlines(width: Int, height: Int, pixels: ByteArray): ByteArray {
        val stride = width * CHANNELS
        val out = ByteArray(height * (stride + 1))
        for (row in 0 until height) {
            val target = row * (stride + 1)
            out[target] = 0
            pixels.copyInto(out, target + 1, row * stride, (row + 1) * stride)
        }
        return out
    }

    /** length, type, payload, CRC of type and payload — the shape every PNG chunk shares. */
    private fun chunk(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.encodeToByteArray()
        val out = ByteArray(payload.size + 12)
        out.writeInt(0, payload.size)
        typeBytes.copyInto(out, 4)
        payload.copyInto(out, 8)
        out.writeInt(payload.size + 8, crc32(typeBytes, payload))
        return out
    }

    private fun ByteArray.writeInt(at: Int, value: Int) {
        this[at] = (value ushr 24).toByte()
        this[at + 1] = (value ushr 16).toByte()
        this[at + 2] = (value ushr 8).toByte()
        this[at + 3] = value.toByte()
    }

    private val CRC_TABLE =
        IntArray(256) { index ->
            var value = index
            repeat(8) { value = if (value and 1 != 0) 0xEDB88320.toInt() xor (value ushr 1) else value ushr 1 }
            value
        }

    private fun crc32(vararg parts: ByteArray): Int {
        var crc = -1
        for (part in parts) {
            for (byte in part) crc = CRC_TABLE[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
        }
        return crc.inv()
    }
}
