package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class XmlConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "xml" || info.mimetype in setOf("text/xml", "application/xml")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val pretty = prettyPrint(bytes.toString(Charsets.UTF_8))
        return Document(blocks = listOf(CodeBlock(pretty, "xml")))
    }

    private fun prettyPrint(xml: String): String = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isIgnoringElementContentWhitespace = true
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        val tf = TransformerFactory.newInstance()
        tf.setAttribute("indent-number", 2)
        val transformer = tf.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        writer.toString().trim().lines().filter { it.isNotBlank() }.joinToString("\n")
    } catch (_: Exception) {
        xml
    }
}
