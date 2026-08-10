package io.github.lemcoder.mikromarkdown

import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.render.MarkdownRenderer
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Pipeline entry point: detect format, parse to a [Document], render Markdown.
 *
 * Rendering happens here rather than inside converters, so every format shares one serializer.
 */
class MikroMarkdown(
    private val mimeDetector: MimeDetector,
    private val renderer: MarkdownRenderer = MarkdownRenderer.Default,
) {
    private val converters = mutableListOf<Pair<DocumentConverter, Double>>()

    fun register(converter: DocumentConverter, priority: Double = 0.0) {
        converters.add(converter to priority)
    }

    fun convert(path: String): ConversionResult = render(parse(path))

    fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult = render(parse(bytes, info))

    /** Parses without rendering, for callers that want the document model itself. */
    fun parse(path: String): Document {
        val info = mimeDetector.detect(path)
        val bytes = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
        return parse(bytes, info)
    }

    fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val sorted = converters.sortedBy { it.second }
        for ((converter, _) in sorted) {
            if (!converter.accepts(bytes, info)) continue
            return try {
                converter.parse(bytes, info)
            } catch (e: MikroMarkdownException) {
                throw e
            } catch (e: Exception) {
                throw FileConversionException(
                    "Conversion failed with ${converter::class.simpleName}: ${e.message}",
                    e,
                )
            }
        }
        throw UnsupportedFormatException(
            "No converter found for: ${info.extension ?: info.mimetype ?: "unknown"}",
        )
    }

    fun render(document: Document): ConversionResult =
        ConversionResult(
            markdown = renderer.render(document),
            title = document.title,
            document = document,
        )
}
