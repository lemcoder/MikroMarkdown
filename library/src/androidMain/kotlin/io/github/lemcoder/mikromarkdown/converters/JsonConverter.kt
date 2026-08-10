package io.github.lemcoder.mikromarkdown.converters

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.Document

class JsonConverter : DocumentConverter {
    private val writer = ObjectMapper().apply {
        registerKotlinModule()
    }.writer(object : DefaultPrettyPrinter() {
        init {
            indentArraysWith(DefaultIndenter("    ", "\n"))
            indentObjectsWith(DefaultIndenter("    ", "\n"))
        }
        override fun createInstance() = this
        override fun writeObjectFieldValueSeparator(g: JsonGenerator) = g.writeRaw(": ")
    })

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "json" || info.mimetype in setOf("application/json", "text/json")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val json = bytes.toString(Charsets.UTF_8)
        val pretty = try {
            writer.writeValueAsString(ObjectMapper().readTree(json))
        } catch (_: Exception) {
            json
        }
        return Document(blocks = listOf(CodeBlock(pretty, "json")))
    }
}
