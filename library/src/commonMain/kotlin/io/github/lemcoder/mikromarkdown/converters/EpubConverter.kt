package io.github.lemcoder.mikromarkdown.converters

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.Strong
import io.github.lemcoder.mikromarkdown.model.Text
import io.github.lemcoder.mikromarkdown.utils.HtmlToDocument
import io.github.lemcoder.mikromarkdown.utils.ZipArchive

/**
 * An EPUB is a ZIP of XHTML.
 *
 * `META-INF/container.xml` names the package document, which lists the manifest and the reading order; the chapters
 * themselves go through the same walker as any other HTML. The XML is read with Ksoup's XML parser rather than a second
 * library, which is why there is no XML dependency.
 */
public class EpubConverter : DocumentConverter {
    private val metaFields =
        listOf(
            "title" to "Title",
            "creator" to "Authors",
            "language" to "Language",
            "description" to "Description",
            "identifier" to "Identifier",
        )

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "epub" || info.mimetype == "application/epub+zip"
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val archive = ZipArchive.open(bytes) ?: return Document()

        val container = archive.readText("META-INF/container.xml") ?: return Document()
        val opfPath = opfPath(container) ?: return Document()
        val opf = archive.readText(opfPath) ?: archive.readText(opfPath.removePrefix("/")) ?: return Document()
        val opfDirectory = opfPath.substringBeforeLast("/", "")

        val packageDocument = Ksoup.parse(html = opf, parser = Parser.xmlParser())
        val metadata = metadata(packageDocument)
        val manifest = manifest(packageDocument)

        val blocks = mutableListOf<Block>()
        var title: String? = metadata["title"]

        for ((key, label) in metaFields) {
            val value = metadata[key] ?: continue
            blocks += Paragraph(listOf(Strong(listOf(Text("$label:"))), Text(" $value")))
        }

        for (idref in spine(packageDocument)) {
            val href = manifest[idref] ?: continue
            val path = if (opfDirectory.isEmpty()) href else "$opfDirectory/$href"
            val html = archive.readText(path) ?: archive.readText(path.removePrefix("/")) ?: continue
            val chapter = HtmlToDocument.parse(html)
            if (title == null) title = chapter.title
            blocks += chapter.blocks
        }

        return Document(blocks = blocks, title = title, metadata = metadata)
    }

    private fun opfPath(container: String): String? =
        Ksoup.parse(html = container, parser = Parser.xmlParser()).selectFirst("rootfile")?.attr("full-path")?.ifEmpty {
            null
        }

    private fun metadata(packageDocument: com.fleeksoft.ksoup.nodes.Document): Map<String, String> {
        val metadata = LinkedHashMap<String, String>()
        for (field in listOf("title", "creator", "language", "description", "identifier")) {
            // Namespaced in the source as dc:title; Ksoup escapes the prefix with a pipe.
            val text = packageDocument.selectFirst("dc|$field")?.text()?.trim()
            if (!text.isNullOrEmpty()) metadata[field] = text
        }
        return metadata
    }

    /** Manifest ids to hrefs, keeping only the documents that carry text. */
    private fun manifest(packageDocument: com.fleeksoft.ksoup.nodes.Document): Map<String, String> {
        val manifest = LinkedHashMap<String, String>()
        for (item in packageDocument.select("manifest > item")) {
            val id = item.attr("id")
            val href = item.attr("href")
            val mediaType = item.attr("media-type")
            if (id.isNotEmpty() && href.isNotEmpty() && mediaType.contains("html")) {
                manifest[id] = href
            }
        }
        return manifest
    }

    private fun spine(packageDocument: com.fleeksoft.ksoup.nodes.Document): List<String> =
        packageDocument.select("spine > itemref").map { it.attr("idref") }.filter { it.isNotEmpty() }
}
