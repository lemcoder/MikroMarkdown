package io.github.lemcoder.mikromarkdown.model

/**
 * Format-independent document model.
 *
 * Every converter parses its input into a [Document]; a single renderer turns
 * documents into Markdown. Output quirks are therefore fixed once, in the
 * renderer, rather than per format.
 */
data class Document(
    val blocks: List<Block> = emptyList(),
    val title: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val assets: List<Asset> = emptyList(),
)

/** An embedded binary resource (image, thumbnail, object) referenced by [Image.assetId]. */
data class Asset(
    val id: String,
    val mediaType: String,
    val bytes: ByteArray,
    val name: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Asset && id == other.id && mediaType == other.mediaType && name == other.name)

    override fun hashCode(): Int = id.hashCode()
}

sealed interface Block

data class Heading(
    val level: Int,
    val content: List<Inline>,
    val anchor: String? = null,
) : Block

data class Paragraph(val content: List<Inline>) : Block

data class CodeBlock(val code: String, val language: String? = null) : Block

data class BlockQuote(val blocks: List<Block>) : Block

data class ListBlock(
    val ordered: Boolean,
    val items: List<ListItem>,
    val start: Int = 1,
    /** GFM loose lists put a blank line between items. */
    val loose: Boolean = false,
) : Block

data class ListItem(
    val blocks: List<Block>,
    /** Non-null makes this a GFM task-list item. */
    val checked: Boolean? = null,
)

data class Table(
    val header: List<TableCell> = emptyList(),
    val rows: List<List<TableCell>> = emptyList(),
    val alignments: List<Alignment> = emptyList(),
    val caption: List<Inline> = emptyList(),
) : Block

data class TableCell(
    val content: List<Inline>,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    constructor(text: String) : this(if (text.isEmpty()) emptyList() else listOf(Text(text)))
}

enum class Alignment { NONE, LEFT, CENTER, RIGHT }

data object ThematicBreak : Block

/** Rendered verbatim as an HTML comment. Used for structural markers such as slide numbers. */
data class HtmlComment(val text: String) : Block

/** Escape hatch for content that is already Markdown (or must not be touched). */
data class RawBlock(val text: String) : Block

sealed interface Inline

data class Text(val value: String) : Inline

data class Strong(val content: List<Inline>) : Inline

data class Emphasis(val content: List<Inline>) : Inline

data class Strikethrough(val content: List<Inline>) : Inline

data class CodeSpan(val code: String) : Inline

data class Link(val content: List<Inline>, val url: String, val title: String? = null) : Inline

data class Image(
    val alt: String,
    val url: String,
    val title: String? = null,
    val assetId: String? = null,
) : Inline

/** Hard line break inside a paragraph. */
data object LineBreak : Inline

/** Inline content that is already Markdown/HTML and must be emitted verbatim. */
data class RawInline(val text: String) : Inline

/** Flattens inline content to its plain-text form (used for titles, anchors, alt text). */
fun List<Inline>.plainText(): String = buildString { appendPlain(this@plainText) }

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
