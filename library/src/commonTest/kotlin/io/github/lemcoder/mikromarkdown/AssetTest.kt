package io.github.lemcoder.mikromarkdown

import com.goncalossilva.resources.Resource
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.render.AssetPolicy
import io.github.lemcoder.mikromarkdown.render.MarkdownOptions
import io.github.lemcoder.mikromarkdown.render.MarkdownRenderer
import io.github.lemcoder.mikromarkdown.utils.ZipArchive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Images a document carries, rather than points at.
 *
 * `Document.assets` had no producer at all once the office formats went, so these cover both ends: a converter putting
 * bytes in, and the Markdown renderer deciding what becomes of them.
 */
class AssetTest {

    private val mid = MikroMarkdown()

    // A 1x1 transparent PNG, small enough to read in the assertions below.
    private val pngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    private fun htmlWithDataUri(): ByteArray =
        """<html><body><p>before <img alt="dot" src="data:image/png;base64,$pngBase64"> after</p></body></html>"""
            .encodeToByteArray()

    @Test
    fun `a data URI becomes an asset rather than a URL the size of the image`() {
        val document = mid.parse(htmlWithDataUri(), StreamInfo(extension = "html"))

        val asset = document.assets.singleOrNull()
        assertNotNull(asset, "the image should have been lifted out of the src")
        assertEquals("image/png", asset.mediaType)
        assertTrue(asset.bytes.size > 60, "the decoded PNG should be bytes, not text")
        // The PNG signature, which is what proves the base64 was decoded rather than copied.
        assertEquals(listOf(-119, 80, 78, 71), asset.bytes.take(4).map { it.toInt() })

        val image = document.blocks.filterIsInstance<Paragraph>().flatMap { it.content }.filterIsInstance<Image>()
        assertEquals(asset.id, image.single().assetId)
    }

    @Test
    fun `a remote image stays a URL`() {
        val html = """<html><body><img alt="x" src="https://example.com/logo.png"></body></html>"""
        val document = mid.parse(html.encodeToByteArray(), StreamInfo(extension = "html"))

        assertTrue(document.assets.isEmpty(), "a reference to somewhere else is not something we carry")
    }

    @Test
    fun `an epub src resolves against the chapter that carries it`() {
        val archive = assertNotNull(ZipArchive.open(Resource("test_files/test.epub").readBytes()))

        // A chapter's own directory, which is what an unqualified src is relative to.
        assertEquals("EPUB/nav.xhtml", archive.resolve("EPUB", "nav.xhtml"))
        // Up and back down, the shape an images/ directory beside the text produces.
        assertEquals("EPUB/chap_1.xhtml", archive.resolve("EPUB/text", "../chap_1.xhtml"))
        assertEquals("EPUB/nav.xhtml", archive.resolve("EPUB", "./nav.xhtml"))
        // Percent escapes are part of the reference, not of the entry name.
        assertEquals("EPUB/nav.xhtml", archive.resolve("EPUB", "nav%2Exhtml"))
        // A fragment addresses a place inside the file, not a different file.
        assertEquals("EPUB/nav.xhtml", archive.resolve("EPUB", "nav.xhtml#toc"))

        // Somewhere else entirely, or nowhere at all.
        assertNull(archive.resolve("EPUB", "https://example.com/logo.png"))
        assertNull(archive.resolve("EPUB", "data:image/png;base64,$pngBase64"))
        assertNull(archive.resolve("EPUB", "images/missing.png"))
        assertNull(archive.resolve("EPUB", ""))
    }

    // ---- what the Markdown renderer does with them -------------------------------------------

    @Test
    fun `the default policy keeps only the alt text`() {
        val markdown = MarkdownRenderer().render(mid.parse(htmlWithDataUri(), StreamInfo(extension = "html")))

        assertContains(markdown, "dot")
        assertFalse(markdown.contains("data:image/png"), "the bytes must not travel as a URL by default")
        assertFalse(markdown.contains("]("), "an alt-only image is not a link")
    }

    @Test
    fun `the inline policy writes the image back as a data URI`() {
        val renderer = MarkdownRenderer(MarkdownOptions(assetPolicy = AssetPolicy.Inline))
        val markdown = renderer.render(mid.parse(htmlWithDataUri(), StreamInfo(extension = "html")))

        assertContains(markdown, "![dot](data:image/png;base64,")
        // Round trip: what comes out is what went in.
        assertContains(markdown, pngBase64)
    }

    @Test
    fun `the reference policy links into the directory the caller will write`() {
        val renderer = MarkdownRenderer(MarkdownOptions(assetPolicy = AssetPolicy.Reference("figures")))
        val markdown = renderer.render(mid.parse(htmlWithDataUri(), StreamInfo(extension = "html")))

        assertContains(markdown, "![dot](figures/")
        assertContains(markdown, ".png)")
    }
}
