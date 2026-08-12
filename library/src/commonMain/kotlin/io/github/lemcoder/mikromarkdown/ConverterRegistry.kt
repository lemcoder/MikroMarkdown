package io.github.lemcoder.mikromarkdown

/**
 * Holds the registered converters in priority order and picks the one that accepts an input.
 *
 * Ordering is applied on registration rather than on every lookup, and the entry list never leaves this class, so
 * callers cannot reorder or inspect the pipeline's dispatch table.
 */
internal class ConverterRegistry {
    private val entries = mutableListOf<Entry>()

    fun register(converter: DocumentConverter, priority: Double) {
        entries += Entry(converter, priority)
        entries.sortBy { it.priority }
    }

    /** The first converter, in priority order, that accepts this input. */
    fun select(bytes: ByteArray, info: StreamInfo): DocumentConverter? =
        entries.firstOrNull { it.converter.accepts(bytes, info) }?.converter

    private data class Entry(val converter: DocumentConverter, val priority: Double)
}
