package com.mikromarkdown.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mikromarkdown.ConversionResult
import com.mikromarkdown.DocumentConverter
import com.mikromarkdown.StreamInfo
import java.io.InputStream

class JsonConverter : DocumentConverter {
    private val mapper = ObjectMapper().apply {
        registerKotlinModule()
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    override fun accepts(stream: InputStream, info: StreamInfo): Boolean {
        return info.extension == "json" || info.mimetype in setOf("application/json", "text/json")
    }

    override fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        val json = stream.readBytes().toString(Charsets.UTF_8)
        val pretty = try {
            val node = mapper.readTree(json)
            mapper.writeValueAsString(node)
        } catch (_: Exception) {
            json
        }
        return ConversionResult(markdown = "```json\n$pretty\n```")
    }
}
