package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.utils.MarkdownNormalizer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

class MarkItDown(
    private val detectMime: (String) -> StreamInfo,
    converters: List<Pair<DocumentConverter, Double>> = emptyList(),
) {
    private val converters = converters.toMutableList()

    fun registerConverter(converter: DocumentConverter, priority: Double = 0.0) {
        converters.add(0, converter to priority)
    }

    fun convert(path: String): ConversionResult {
        val info = detectMime(path)
        val bytes = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
        return dispatch(bytes, info)
    }

    fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult {
        return dispatch(bytes, info)
    }

    private fun dispatch(bytes: ByteArray, info: StreamInfo): ConversionResult {
        val sorted = converters.sortedBy { it.second }
        for ((converter, _) in sorted) {
            if (converter.accepts(bytes, info)) {
                val result = try {
                    converter.convert(bytes, info)
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
