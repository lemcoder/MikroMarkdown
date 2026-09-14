package io.github.lemcoder.mikromarkdown.pdf

import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Paragraph
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Images pulled out of a PDF.
 *
 * The fixture is a hand-built PDF holding one 2x2 uncompressed DeviceRGB image, because the paper this module's other
 * tests use is text only. Small enough that every pixel can be asserted, which is what separates "produced some bytes"
 * from "produced the right picture".
 */
class PdfImageTest {

    private val fixture = File("src/jvmTest/resources/image.pdf")

    @Test
    fun `an image in a page becomes an asset`() {
        val pages = extractPages(fixture.readBytes())

        val image = assertNotNull(pages.single().images.singleOrNull(), "expected one image on the page")
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals("image/png", image.asset.mediaType, "raw pixels have no container, so one is written")
    }

    @Test
    fun `the pixels survive the trip through the encoder`() {
        val image = extractPages(fixture.readBytes()).single().images.single()

        val decoded = assertNotNull(ImageIO.read(image.asset.bytes.inputStream()), "not a readable PNG")
        assertEquals(2, decoded.width)
        assertEquals(2, decoded.height)

        // The fixture's four pixels, in order: red, green, blue, white.
        assertEquals(0xFFFF0000.toInt(), decoded.getRGB(0, 0))
        assertEquals(0xFF00FF00.toInt(), decoded.getRGB(1, 0))
        assertEquals(0xFF0000FF.toInt(), decoded.getRGB(0, 1))
        assertEquals(0xFFFFFFFF.toInt(), decoded.getRGB(1, 1))
    }

    @Test
    fun `the document carries the image and points at it`() {
        val document = PdfiumConverter().parse(fixture.readBytes(), StreamInfo(extension = "pdf"))

        val asset = assertNotNull(document.assets.singleOrNull())
        val figure =
            document.blocks
                .filterIsInstance<Paragraph>()
                .flatMap { it.content }
                .filterIsInstance<Image>()
                .singleOrNull()
        assertNotNull(figure, "the image should reach the model as an Image inline")
        assertEquals(asset.id, figure.assetId, "the inline must point at the asset the document carries")
        assertTrue(figure.url.isEmpty(), "an asset has bytes, not an address")
    }

    @Test
    fun `a text-only pdf yields no images`() {
        val paper = File("../library/src/commonTest/resources/test_files/test.pdf")
        val document = PdfiumConverter().parse(paper.readBytes(), StreamInfo(extension = "pdf"))

        assertTrue(document.assets.isEmpty(), "nothing should be invented for a page of text")
    }
}
