package io.github.lemcoder.mikromarkdown

interface DocumentConverter {
    fun accepts(bytes: ByteArray, info: StreamInfo): Boolean
    fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult
}
