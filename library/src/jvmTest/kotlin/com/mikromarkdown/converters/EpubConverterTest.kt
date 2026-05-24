package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertTrue

class EpubConverterTest {
    private val converter = EpubConverter()
    private val info = StreamInfo(extension = "epub")

    @Test
    fun `accepts epub extension`() {
        assertTrue(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `simple epub content is extracted`() {
        val baos = buildEpub(
            chapters = listOf(
                "chapter1.xhtml" to "<html><body><h1>Chapter One</h1><p>First chapter content.</p></body></html>",
                "chapter2.xhtml" to "<html><body><h1>Chapter Two</h1><p>Second chapter content.</p></body></html>",
            ),
        )
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("Chapter One"), "Expected chapter title in:\n${result.markdown}")
        assertTrue(result.markdown.contains("First chapter content"), "Expected chapter content in:\n${result.markdown}")
        assertTrue(result.markdown.contains("Chapter Two"))
    }

    private fun buildEpub(chapters: List<Pair<String, String>>): ByteArrayOutputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray())
            zip.closeEntry()

            val manifestItems = chapters.mapIndexed { i, (name, _) ->
                """<item id="chapter${i + 1}" href="$name" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n    ")

            val spineItems = chapters.indices.joinToString("\n    ") { i ->
                """<itemref idref="chapter${i + 1}"/>"""
            }

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write("""<?xml version="1.0"?>
<package version="2.0" xmlns="http://www.idpf.org/2007/opf">
  <metadata/>
  <manifest>
    $manifestItems
  </manifest>
  <spine>
    $spineItems
  </spine>
</package>""".toByteArray())
            zip.closeEntry()

            for ((name, content) in chapters) {
                zip.putNextEntry(ZipEntry("OEBPS/$name"))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return baos
    }
}
