package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.Document

/**
 * Re-indents XML for readability, without a parser.
 *
 * The JVM build hands this to javax.xml; on native the job is only whitespace, so the document is split into tags and
 * text and re-emitted at the right depth. Anything unexpected leaves the input untouched, which is also what the JVM
 * converter does when parsing fails.
 */
public class XmlConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "xml" || info.mimetype in setOf("text/xml", "application/xml")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = listOf(CodeBlock(prettyPrint(bytes.decodeToString()), "xml")))

    private fun prettyPrint(xml: String): String {
        val lines = mutableListOf<String>()
        var depth = 0
        var index = 0

        while (index < xml.length) {
            val open = xml.indexOf('<', index)
            if (open < 0) break
            val close = xml.indexOf('>', open)
            if (close < 0) return xml

            val precedingText = xml.substring(index, open).trim()
            if (precedingText.isNotEmpty()) lines += "  ".repeat(depth) + precedingText

            val tag = xml.substring(open, close + 1)
            when (kindOf(tag)) {
                TagKind.CLOSING -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    lines += "  ".repeat(depth) + tag
                }

                // The JVM formatter omits the XML declaration; keep both targets identical.
                TagKind.STANDALONE -> if (!tag.startsWith("<?xml")) lines += "  ".repeat(depth) + tag

                TagKind.OPENING -> {
                    // <title>text</title> stays on one line, as the JVM formatter writes it.
                    val leaf = leafElement(xml, close + 1)
                    if (leaf == null) {
                        lines += "  ".repeat(depth) + tag
                        depth++
                    } else {
                        lines += "  ".repeat(depth) + tag + leaf.text + leaf.closingTag
                        index = leaf.endIndex
                        continue
                    }
                }
            }
            index = close + 1
        }

        return lines.joinToString("\n").trim().ifEmpty { xml }
    }

    /** Text followed directly by a closing tag, meaning the element has no children. */
    private fun leafElement(xml: String, from: Int): Leaf? {
        val nextOpen = xml.indexOf('<', from)
        if (nextOpen < 0) return null
        val nextClose = xml.indexOf('>', nextOpen)
        if (nextClose < 0) return null

        val tag = xml.substring(nextOpen, nextClose + 1)
        if (kindOf(tag) != TagKind.CLOSING) return null

        val text = xml.substring(from, nextOpen).trim()
        if (text.isEmpty() || text.contains('\n')) return null

        return Leaf(text, tag, nextClose + 1)
    }

    private class Leaf(val text: String, val closingTag: String, val endIndex: Int)

    private fun kindOf(tag: String): TagKind =
        when {
            tag.startsWith("<?") || tag.startsWith("<!") -> TagKind.STANDALONE
            tag.startsWith("</") -> TagKind.CLOSING
            tag.endsWith("/>") -> TagKind.STANDALONE
            else -> TagKind.OPENING
        }

    private enum class TagKind {
        OPENING,
        CLOSING,
        STANDALONE,
    }
}
