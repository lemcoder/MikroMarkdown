package io.github.lemcoder.mikromarkdown.model

/**
 * Format-independent document model.
 *
 * Every converter parses its input into a [Document]; a single renderer turns documents into Markdown. Output quirks
 * are therefore fixed once, in the renderer, rather than per format.
 */
public data class Document(
    val blocks: List<Block> = emptyList(),
    val title: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val assets: List<Asset> = emptyList(),
)

/** An embedded binary resource (image, thumbnail, object) referenced by [Image.assetId]. */
public data class Asset(
    val id: String,
    val mediaType: String,
    val bytes: ByteArray,
    val name: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Asset && id == other.id && mediaType == other.mediaType && name == other.name)

    override fun hashCode(): Int = id.hashCode()
}

public sealed interface Block

public data class Heading(
    val level: Int,
    val content: List<Inline>,
    val anchor: String? = null,
) : Block

public data class Paragraph(val content: List<Inline>) : Block

public data class CodeBlock(val code: String, val language: String? = null) : Block

public data class BlockQuote(val blocks: List<Block>) : Block

public data class ListBlock(
    val ordered: Boolean,
    val items: List<ListItem>,
    val start: Int = 1,
    /** GFM loose lists put a blank line between items. */
    val loose: Boolean = false,
) : Block

public data class ListItem(
    val blocks: List<Block>,
    /** Non-null makes this a GFM task-list item. */
    val checked: Boolean? = null,
)

public data class Table(
    val header: List<TableCell> = emptyList(),
    val rows: List<List<TableCell>> = emptyList(),
    val alignments: List<Alignment> = emptyList(),
    val caption: List<Inline> = emptyList(),
) : Block

public data class TableCell(
    val content: List<Inline>,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    public constructor(text: String) : this(if (text.isEmpty()) emptyList() else listOf(Text(text)))
}

public enum class Alignment {
    NONE,
    LEFT,
    CENTER,
    RIGHT,
}

public data object ThematicBreak : Block

/** Rendered verbatim as an HTML comment. Used for structural markers such as slide numbers. */
public data class HtmlComment(val text: String) : Block

/** Escape hatch for content that is already Markdown (or must not be touched). */
public data class RawBlock(val text: String) : Block

public sealed interface Inline

public data class Text(val value: String) : Inline

public data class Strong(val content: List<Inline>) : Inline

public data class Emphasis(val content: List<Inline>) : Inline

public data class Strikethrough(val content: List<Inline>) : Inline

public data class CodeSpan(val code: String) : Inline

public data class Link(val content: List<Inline>, val url: String, val title: String? = null) : Inline

public data class Image(
    val alt: String,
    val url: String,
    val title: String? = null,
    val assetId: String? = null,
) : Inline

/** Hard line break inside a paragraph. */
public data object LineBreak : Inline

/** Inline content that is already Markdown/HTML and must be emitted verbatim. */
public data class RawInline(val text: String) : Inline

/** Flattens inline content to its plain-text form (used for titles, anchors, alt text). */
public fun List<Inline>.plainText(): String = buildString { appendPlain(this@plainText) }

private fun StringBuilder.appendPlain(inlines: List<Inline>) {
    for (inline in inlines) {
        when (inline) {
            is Text -> append(inline.value)
            is Strong -> appendPlain(inline.content)
            is Emphasis -> appendPlain(inline.content)
            is Strikethrough -> appendPlain(inline.content)
            is CodeSpan -> append(inline.code)
            is Link -> appendPlain(inline.content)
            is Image -> append(inline.alt)
            is RawInline -> append(inline.text)
            LineBreak -> append(' ')
        }
    }
}
