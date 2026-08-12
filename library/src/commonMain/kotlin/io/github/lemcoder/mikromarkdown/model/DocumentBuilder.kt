package io.github.lemcoder.mikromarkdown.model

/**
 * Small builder used by converters so parsing code stays free of Markdown syntax.
 *
 * ```
 * document {
 *     heading(1, "Report")
 *     paragraph("Body text")
 *     table(header = listOf("A", "B"), rows = listOf(listOf("1", "2")))
 * }
 * ```
 */
/** Restricts builder receivers so an inline block cannot silently call document-level methods. */
@DslMarker public annotation class DocumentDsl

@DocumentDsl public fun document(block: DocumentBuilder.() -> Unit): Document = DocumentBuilder().apply(block).build()

@DocumentDsl
public class DocumentBuilder {
    private val blocks = mutableListOf<Block>()
    private val assets = mutableListOf<Asset>()
    private val metadata = mutableMapOf<String, String>()
    private var title: String? = null

    public fun add(block: Block) {
        blocks += block
    }

    public fun addAll(blocks: Iterable<Block>) {
        this.blocks += blocks
    }

    public fun asset(asset: Asset) {
        assets += asset
    }

    public fun meta(key: String, value: String?) {
        if (!value.isNullOrBlank()) metadata[key] = value
    }

    /** Records [text] as the document title unless one was already found. */
    public fun titleIfAbsent(text: String?) {
        if (title == null && !text.isNullOrBlank()) title = text
    }

    /** The title recorded so far, if any. */
    public fun title(): String? = title

    public fun heading(level: Int, text: String) {
        if (text.isNotBlank()) add(Heading(level, listOf(Text(text))))
    }

    public fun heading(level: Int, content: List<Inline>) {
        if (content.isNotEmpty()) add(Heading(level, content))
    }

    public fun paragraph(text: String) {
        if (text.isNotBlank()) add(Paragraph(listOf(Text(text))))
    }

    public fun paragraph(content: List<Inline>) {
        if (content.isNotEmpty()) add(Paragraph(content))
    }

    public fun code(code: String, language: String? = null) {
        add(CodeBlock(code, language))
    }

    public fun comment(text: String) {
        add(HtmlComment(text))
    }

    public fun raw(text: String) {
        if (text.isNotBlank()) add(RawBlock(text))
    }

    public fun bulletList(items: List<String>) {
        if (items.isEmpty()) return
        add(ListBlock(ordered = false, items = items.map { ListItem(listOf(Paragraph(listOf(Text(it))))) }))
    }

    public fun table(
        header: List<String>,
        rows: List<List<String>>,
        alignments: List<Alignment> = emptyList(),
    ) {
        if (header.isEmpty() && rows.isEmpty()) return
        add(
            Table(
                header = header.map { TableCell(it) },
                rows = rows.map { row -> row.map { TableCell(it) } },
                alignments = alignments,
            )
        )
    }

    public fun build(): Document =
        Document(
            blocks = blocks.toList(),
            title = title,
            metadata = metadata.toMap(),
            assets = assets.toList(),
        )
}

/** Builds a list of inlines without repeating `listOf(...)` wrappers in parsers. */
@DocumentDsl public fun inlines(block: InlineBuilder.() -> Unit): List<Inline> = InlineBuilder().apply(block).build()

@DocumentDsl
public class InlineBuilder {
    private val items = mutableListOf<Inline>()

    public fun text(value: String) {
        if (value.isNotEmpty()) items += Text(value)
    }

    public fun strong(value: String) {
        if (value.isNotEmpty()) items += Strong(listOf(Text(value)))
    }

    public fun emphasis(value: String) {
        if (value.isNotEmpty()) items += Emphasis(listOf(Text(value)))
    }

    public fun code(value: String) {
        if (value.isNotEmpty()) items += CodeSpan(value)
    }

    public fun link(text: String, url: String) {
        items += Link(listOf(Text(text)), url)
    }

    public fun image(alt: String, url: String, assetId: String? = null) {
        items += Image(alt, url, assetId = assetId)
    }

    public fun lineBreak() {
        items += LineBreak
    }

    public operator fun plusAssign(inline: Inline) {
        items += inline
    }

    public operator fun plusAssign(inlines: List<Inline>) {
        items += inlines
    }

    public fun build(): List<Inline> = items.toList()
}

/** Wraps [content] in the emphasis combination described by the flags. */
public fun styled(content: List<Inline>, bold: Boolean, italic: Boolean, strike: Boolean = false): List<Inline> {
    var result = content
    if (strike) result = listOf(Strikethrough(result))
    if (italic) result = listOf(Emphasis(result))
    if (bold) result = listOf(Strong(result))
    return result
}
