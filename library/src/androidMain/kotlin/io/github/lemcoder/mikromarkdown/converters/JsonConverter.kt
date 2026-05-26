package io.github.lemcoder.mikromarkdown.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.lemcoder.mikromarkdown.ConversionResult
import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo

class JsonConverter : DocumentConverter {
    private val mapper = ObjectMapper().apply {
        registerKotlinModule()
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "json" || info.mimetype in setOf("application/json", "text/json")
    }

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val json = bytes.toString(Charsets.UTF_8)
        val pretty = try {
            val node = mapper.readTree(json)
            mapper.writeValueAsString(node)
        } catch (_: Exception) {
            json
        }
        return ConversionResult(markdown = "```json\n$pretty\n```")
    }
}
