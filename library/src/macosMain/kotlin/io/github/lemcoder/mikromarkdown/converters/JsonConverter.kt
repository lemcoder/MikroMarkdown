package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.Document
import kotlinx.serialization.json.Json

/** Pretty-prints JSON through kotlinx-serialization, the JVM build's Jackson having no native port. */
public class JsonConverter : DocumentConverter {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
        isLenient = true
    }

    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "json" || info.mimetype in setOf("application/json", "text/json")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val text = bytes.decodeToString()
        val pretty =
            try {
                json.encodeToString(Json.parseToJsonElement(text))
            } catch (_: Exception) {
                text
            }
        return Document(blocks = listOf(CodeBlock(pretty, "json")))
    }
}
