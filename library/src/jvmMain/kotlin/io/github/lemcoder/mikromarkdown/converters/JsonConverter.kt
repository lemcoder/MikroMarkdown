package io.github.lemcoder.mikromarkdown.converters

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo

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

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val json = bytes.toString(Charsets.UTF_8)
        val pretty = try {
            val node = ObjectMapper().readTree(json)
            writer.writeValueAsString(node)
        } catch (_: Exception) {
            json
        }
        return ConversionResult(markdown = pretty)
    }
}
