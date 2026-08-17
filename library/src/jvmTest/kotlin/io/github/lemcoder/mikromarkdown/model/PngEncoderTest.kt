package io.github.lemcoder.mikromarkdown.model

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The encoder is checked by decoding what it writes with an independent decoder — ImageIO — rather than by comparing
 * bytes against a recording. A PNG we wrote and only we can read would pass a golden-file test and still be useless to
 * a Compose or SwiftUI reader.
 */
class PngEncoderTest {

    @Test
    fun `ImageIO reads back every pixel, alpha included`() {
        val width = 7
        val height = 5
        val pixels = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val at = (y * width + x) * 4
                pixels[at] = (x * 30).toByte()
                pixels[at + 1] = (y * 50).toByte()
                pixels[at + 2] = ((x + y) * 20).toByte()
                pixels[at + 3] = (255 - x * 10).toByte()
            }
        }

        val png = assertNotNull(PngEncoder.encode(width, height, pixels))
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(png)), "ImageIO could not read it")

        assertEquals(width, image.width)
        assertEquals(height, image.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val at = (y * width + x) * 4
                val argb = image.getRGB(x, y)
                assertEquals(pixels[at].toInt() and 0xFF, (argb shr 16) and 0xFF, "red at $x,$y")
                assertEquals(pixels[at + 1].toInt() and 0xFF, (argb shr 8) and 0xFF, "green at $x,$y")
                assertEquals(pixels[at + 2].toInt() and 0xFF, argb and 0xFF, "blue at $x,$y")
                assertEquals(pixels[at + 3].toInt() and 0xFF, (argb ushr 24) and 0xFF, "alpha at $x,$y")
            }
        }
    }

    @Test
    fun `the signature and chunk order are what a decoder expects`() {
        val png = assertNotNull(PngEncoder.encode(1, 1, ByteArray(4)))

        assertEquals(listOf(137, 80, 78, 71, 13, 10, 26, 10), png.take(8).map { it.toInt() and 0xFF })
        val text = png.decodeToString()
        assertEquals(listOf("IHDR", "IDAT", "IEND"), listOf("IHDR", "IDAT", "IEND").sortedBy { text.indexOf(it) })
    }

    @Test
    fun `dimensions that disagree with the buffer are refused`() {
        assertNull(PngEncoder.encode(2, 2, ByteArray(4)), "a short buffer must not produce a truncated image")
        assertNull(PngEncoder.encode(0, 4, ByteArray(0)))
    }
}
