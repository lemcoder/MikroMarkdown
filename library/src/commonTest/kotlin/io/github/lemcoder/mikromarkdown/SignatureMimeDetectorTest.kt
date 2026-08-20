package io.github.lemcoder.mikromarkdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins what the leading bytes decide and what the extension decides.
 *
 * The integration tests hand converters a [StreamInfo] they build themselves, so detection is only covered here.
 */
class SignatureMimeDetectorTest {

    private fun detect(filename: String, vararg signature: Int): String? =
        SignatureMimeDetector.detect(filename, ByteArray(signature.size) { signature[it].toByte() }).mimetype

    private fun detect(filename: String, signature: String): String? =
        SignatureMimeDetector.detect(filename, signature.encodeToByteArray()).mimetype

    @Test
    fun `content wins over a mislabelled extension`() {
        assertEquals("application/pdf", detect("report.txt", "%PDF-1.7"))
    }

    @Test
    fun `a ZIP package is told apart by its extension`() {
        assertEquals("application/epub+zip", detect("book.epub", "PK\u0003\u0004"))
        // Every other OOXML container is a package this library does not read; it is reported as one.
        assertEquals("application/zip", detect("sheet.xlsx", "PK\u0003\u0004"))
        assertEquals("application/zip", detect("archive.zip", "PK\u0003\u0004"))
    }

    @Test
    fun `a legacy OLE compound file is named rather than left unknown`() {
        assertEquals("application/x-ole-storage", detect("memo.doc", 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1))
    }

    @Test
    fun `formats with no signature are recognised by extension`() {
        assertEquals("text/csv", detect("data.csv", "a,b,c\n1,2,3"))
        assertEquals("text/markdown", detect("notes.md", "# Title"))
    }

    @Test
    fun `an unknown extension with no signature is left undecided`() {
        assertNull(detect("mystery.bin", "not a signature"))
        assertNull(detect("noextension", ""))
    }

    @Test
    fun `a file shorter than the signature is read whole rather than failing`() {
        assertEquals("text/plain", detect("tiny.txt", "hi"))
    }

    @Test
    fun `the path is split into filename and extension`() {
        val info = SignatureMimeDetector.detect("/tmp/some dir/Book.EPUB", "PK\u0003\u0004".encodeToByteArray())

        assertEquals("Book.EPUB", info.filename)
        assertEquals("epub", info.extension)
        assertEquals("application/epub+zip", info.mimetype)
    }
}
