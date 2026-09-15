package io.github.lemcoder.mikromarkdown.compose

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lemcoder.mikromarkdown.model.Asset
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.PngEncoder
import io.github.lemcoder.mikromarkdown.model.Text
import java.io.File
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the renderer actually puts on a screen.
 *
 * Instrumented rather than a unit test because the question is whether pixels arrive: a PNG in `Document.assets` has to
 * be decoded, laid out and drawn, and only a device does all three. The image assertions read the captured frame, so
 * they fail if the bitmap is dropped, scaled to nothing or replaced by its alt text.
 */
class DocumentViewTest {

    @get:Rule val compose = createComposeRule()

    /** A solid block of one colour, big enough that a pixel in the middle is unambiguous. */
    private fun solidPng(color: Int, size: Int = 64): ByteArray {
        val pixels = ByteArray(size * size * 4)
        for (pixel in 0 until size * size) {
            pixels[pixel * 4] = (color shr 16 and 0xFF).toByte()
            pixels[pixel * 4 + 1] = (color shr 8 and 0xFF).toByte()
            pixels[pixel * 4 + 2] = (color and 0xFF).toByte()
            pixels[pixel * 4 + 3] = 0xFF.toByte()
        }
        return requireNotNull(PngEncoder.encode(size, size, pixels))
    }

    private fun documentWithFigure(color: Int): Document {
        val asset = Asset(id = "figure", mediaType = "image/png", bytes = solidPng(color), name = "figure.png")
        return Document(
            blocks =
                listOf(
                    Heading(1, "Figure follows"),
                    Paragraph(listOf(Image(alt = "a solid square", url = "", assetId = asset.id))),
                ),
            assets = listOf(asset),
        )
    }

    @Test
    fun anAssetBackedImageIsDrawn() {
        val magenta = 0xFF00FF
        compose.setContent { MaterialTheme { DocumentView(documentWithFigure(magenta), Modifier.fillMaxSize()) } }
        compose.waitForIdle()

        val frame = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(frame.containsColour(magenta), "the figure's colour never reached the screen")
    }

    @Test
    fun anImageWithNoAssetFallsBackToItsAltText() {
        // A remote URL is a reference to somewhere else, and the renderer fetches nothing.
        val document =
            Document(
                blocks = listOf(Paragraph(listOf(Image(alt = "remote picture", url = "https://example.com/x.png"))))
            )
        compose.setContent { MaterialTheme { DocumentView(document, Modifier.fillMaxSize()) } }

        compose.onNodeWithText("remote picture").assertIsDisplayed()
    }

    @Test
    fun textAndHeadingsReachTheScreen() {
        val document = Document(blocks = listOf(Heading(1, "A heading"), Paragraph(listOf(Text("Body text here.")))))
        compose.setContent { MaterialTheme { DocumentView(document, Modifier.fillMaxSize()) } }

        compose.onNodeWithText("A heading").assertIsDisplayed()
        compose.onNodeWithText("Body text here.").assertIsDisplayed()
    }

    /**
     * Writes the rendered frame where the build can pull it.
     *
     * Not an assertion — it is how a change to the layout gets looked at rather than guessed at.
     */
    @Test
    fun captureForReview() {
        compose.setContent { MaterialTheme { DocumentView(sampleDocument(), Modifier.fillMaxSize()) } }
        compose.waitForIdle()

        val frame = compose.onRoot().captureToImage().asAndroidBitmap()
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "document-view.png")
        target.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(target.length() > 0, "nothing was captured")
    }

    private fun sampleDocument(): Document {
        val asset = Asset(id = "figure", mediaType = "image/png", bytes = solidPng(0x3F51B5), name = "figure.png")
        return Document(
            title = "MikroMarkdown",
            blocks =
                listOf(
                    Heading(1, "Rendering a Document"),
                    Paragraph(listOf(Text("The model goes straight to the screen — no Markdown in between."))),
                    Heading(2, "A figure"),
                    Paragraph(listOf(Image(alt = "a solid square", url = "", assetId = asset.id))),
                ),
            assets = listOf(asset),
        )
    }

    /** Sampling beats an exact pixel: the image is centred and scaled, so its position is not fixed. */
    private fun Bitmap.containsColour(rgb: Int): Boolean {
        val target = Color.rgb(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                if (getPixel(x, y) == target) return true
            }
        }
        return false
    }
}
