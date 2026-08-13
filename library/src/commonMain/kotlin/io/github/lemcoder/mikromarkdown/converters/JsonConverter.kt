package io.github.lemcoder.mikromarkdown.converters

import io.github.lemcoder.mikromarkdown.DocumentConverter
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.utils.JsonFormatter

/** Pretty-prints JSON. The re-indenter is a few dozen lines, where Jackson was 469 loaded classes. */
public class JsonConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean {
        return info.extension == "json" || info.mimetype in setOf("application/json", "text/json")
    }

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = listOf(CodeBlock(JsonFormatter.prettyPrint(bytes.decodeToString()), "json")))
}
