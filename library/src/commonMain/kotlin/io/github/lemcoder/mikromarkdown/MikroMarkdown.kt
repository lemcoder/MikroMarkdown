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
public class MikroMarkdown(
    private val mimeDetector: MimeDetector,
    private val renderer: MarkdownRenderer = MarkdownRenderer.Default,
) {
    private val converters = ConverterRegistry()

    /** Lower priority runs first; [io.github.lemcoder.mikromarkdown.converters.PlainTextConverter] uses 10.0. */
    public fun register(converter: DocumentConverter, priority: Double = 0.0) {
        converters.register(converter, priority)
    }

    public fun convert(path: String): ConversionResult = render(parse(path))

    public fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult = render(parse(bytes, info))

    /** Parses without rendering, for callers that want the document model itself. */
    public fun parse(path: String): Document {
        val bytes = SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
        return parse(bytes, mimeDetector.detect(path, bytes))
    }

    public fun parse(bytes: ByteArray, info: StreamInfo): Document {
        val converter =
            converters.select(bytes, info)
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

    public fun render(document: Document): ConversionResult =
        ConversionResult(
            markdown = renderer.render(document),
            title = document.title,
            document = document,
        )
}
