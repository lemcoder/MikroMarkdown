package com.mikromarkdown

import java.io.InputStream

interface DocumentConverter {
    fun accepts(stream: InputStream, info: StreamInfo): Boolean
    fun convert(stream: InputStream, info: StreamInfo): ConversionResult
}
