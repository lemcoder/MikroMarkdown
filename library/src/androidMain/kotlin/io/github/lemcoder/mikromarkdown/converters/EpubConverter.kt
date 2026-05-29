package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.utils.HtmlToMarkdown
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class EpubConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "epub" || info.mimetype == "application/epub+zip"
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val entries = readZip(bytes)

        val containerXml = entries["META-INF/container.xml"] ?: return ConversionResult(markdown = "")
        val opfPath = parseOpfPath(containerXml) ?: return ConversionResult(markdown = "")

        val opfBytes = entries[opfPath] ?: entries[opfPath.removePrefix("/")] ?: return ConversionResult(markdown = "")
        val opfDir = opfPath.substringBeforeLast("/", "")

        val (manifest, spine, metadata) = parseOpf(opfBytes)

        val sb = StringBuilder()
        var title: String? = metadata["title"]

        val metaFields = listOf("title" to "Title", "creator" to "Authors", "language" to "Language",
            "description" to "Description", "identifier" to "Identifier")
        for ((key, label) in metaFields) {
            metadata[key]?.let { sb.appendLine("**$label:** $it") }
        }
        if (sb.isNotEmpty()) sb.appendLine()

        for (idref in spine) {
            val href = manifest[idref] ?: continue
            val fullPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
            val htmlBytes = entries[fullPath] ?: entries[fullPath.removePrefix("/")] ?: continue
            val html = htmlBytes.toString(Charsets.UTF_8)
            val (markdown, chapterTitle) = HtmlToMarkdown.convert(html)
            if (title == null && chapterTitle != null) title = chapterTitle
            if (markdown.isNotBlank()) {
                sb.appendLine(markdown)
                sb.appendLine()
            }
        }

        return ConversionResult(markdown = sb.toString(), title = title)
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun parseOpfPath(containerXml: ByteArray): String? {
        val doc = parseXml(containerXml) ?: return null
        val rootfiles = doc.getElementsByTagName("rootfile")
        if (rootfiles.length == 0) return null
        return (rootfiles.item(0) as? Element)?.getAttribute("full-path")
    }

    private fun parseOpf(opfBytes: ByteArray): Triple<Map<String, String>, List<String>, Map<String, String>> {
        val doc = parseXml(opfBytes) ?: return Triple(emptyMap(), emptyList(), emptyMap())

        val metadata = mutableMapOf<String, String>()
        for (tag in listOf("dc:title", "dc:creator", "dc:language", "dc:description", "dc:identifier")) {
            val nodes = doc.getElementsByTagName(tag)
            if (nodes.length > 0) {
                val text = nodes.item(0).textContent?.trim()
                if (!text.isNullOrEmpty()) {
                    metadata[tag.removePrefix("dc:")] = text
                }
            }
        }

        val manifest = mutableMapOf<String, String>()
        val manifestItems = doc.getElementsByTagName("item")
        for (i in 0 until manifestItems.length) {
            val item = manifestItems.item(i) as? Element ?: continue
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            val mediaType = item.getAttribute("media-type")
            if (id.isNotEmpty() && href.isNotEmpty() &&
                (mediaType.contains("html") || mediaType.contains("xhtml"))) {
                manifest[id] = href
            }
        }

        val spine = mutableListOf<String>()
        val itemrefs = doc.getElementsByTagName("itemref")
        for (i in 0 until itemrefs.length) {
            val itemref = itemrefs.item(i) as? Element ?: continue
            val idref = itemref.getAttribute("idref")
            if (idref.isNotEmpty()) spine.add(idref)
        }

        return Triple(manifest, spine, metadata)
    }

    private fun parseXml(bytes: ByteArray) = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        factory.newDocumentBuilder().parse(InputSource(StringReader(bytes.toString(Charsets.UTF_8))))
    } catch (_: Exception) {
        null
    }
}
