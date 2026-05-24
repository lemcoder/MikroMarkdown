package com.mikromarkdown.converters

import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class XmlConverter : DocumentConverter {
    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension == "xml" || info.mimetype in setOf("text/xml", "application/xml")
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val xml = stream.readBytes().toString(Charsets.UTF_8)
        val pretty = prettyPrint(xml)
        return ConversionResult(markdown = "```xml\n$pretty\n```")
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
