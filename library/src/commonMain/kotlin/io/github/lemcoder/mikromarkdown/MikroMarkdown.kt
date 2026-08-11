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
        val converter =
            converters.sortedBy { it.second }.firstOrNull { (candidate, _) -> candidate.accepts(bytes, info) }?.first
                ?: throw UnsupportedFormatException(
                    "No converter found for: ${info.extension ?: info.mimetype ?: "unknown"}"
                )

        return converter.parseOrFail(bytes, info)
    }

    /** Converter failures surface as [FileConversionException]; our own exceptions pass through. */
    private fun DocumentConverter.parseOrFail(bytes: ByteArray, info: StreamInfo): Document =
        try {
            parse(bytes, info)
        } catch (e: MikroMarkdownException) {
            throw e
        } catch (e: Exception) {
            throw FileConversionException("Conversion failed with ${this::class.simpleName}: ${e.message}", e)
        }

    fun render(document: Document): ConversionResult =
        ConversionResult(
            markdown = renderer.render(document),
            title = document.title,
            document = document,
        )
}
