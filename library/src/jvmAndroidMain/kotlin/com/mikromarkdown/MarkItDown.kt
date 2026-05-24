package com.mikromarkdown

import com.mikromarkdown.converters.*
import com.mikromarkdown.utils.MarkdownNormalizer
import java.io.File
import java.io.InputStream

class MarkItDown(
    private val detectMime: (File) -> StreamInfo,
    extraConverters: List<Pair<DocumentConverter, Double>> = emptyList(),
) {
    private val converters = mutableListOf<Pair<DocumentConverter, Double>>()

    init {
        register(MarkdownPassthroughConverter(), 0.0)
        register(HtmlConverter(), 0.0)
        register(CsvConverter(), 0.0)
        register(JsonConverter(), 0.0)
        register(XmlConverter(), 0.0)
        register(DocxConverter(), 0.0)
        register(XlsxConverter(), 0.0)
        register(PptxConverter(), 0.0)
        register(EpubConverter(), 0.0)
        extraConverters.forEach { (converter, priority) -> register(converter, priority) }
        register(PlainTextConverter(), 10.0)
    }

    fun registerConverter(converter: DocumentConverter, priority: Double = 0.0) {
        converters.add(0, converter to priority)
    }

    fun convert(file: File): ConversionResult {
        val info = detectMime(file)
        return dispatch(file.readBytes(), info)
    }

    fun convert(stream: InputStream, info: StreamInfo): ConversionResult {
        return dispatch(stream.readBytes(), info)
    }

    private fun register(converter: DocumentConverter, priority: Double) {
        converters.add(converter to priority)
    }

    private fun dispatch(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val sorted = converters.sortedBy { it.second }
        for ((converter, _) in sorted) {
            if (converter.accepts(bytes.inputStream(), info)) {
                val result = try {
                    converter.convert(bytes.inputStream(), info)
                } catch (e: MarkItDownException) {
                    throw e
                } catch (e: Exception) {
                    throw FileConversionException(
                        "Conversion failed with ${converter::class.simpleName}: ${e.message}",
                        e,
                    )
                }
                return result.copy(markdown = MarkdownNormalizer.normalize(result.markdown))
            }
        }
        throw UnsupportedFormatException(
            "No converter found for: ${info.extension ?: info.mimetype ?: "unknown"}",
        )
    }
}
