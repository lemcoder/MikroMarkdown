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
fun document(block: DocumentBuilder.() -> Unit): Document = DocumentBuilder().apply(block).build()

class DocumentBuilder {
    private val blocks = mutableListOf<Block>()
    private val assets = mutableListOf<Asset>()
    private val metadata = mutableMapOf<String, String>()
    var title: String? = null

    fun add(block: Block) {
        blocks += block
    }

    fun addAll(blocks: Iterable<Block>) {
        this.blocks += blocks
    }

    fun asset(asset: Asset) {
        assets += asset
    }

    fun meta(key: String, value: String?) {
        if (!value.isNullOrBlank()) metadata[key] = value
    }

    /** Records [text] as the document title unless one was already found. */
    fun titleIfAbsent(text: String?) {
        if (title == null && !text.isNullOrBlank()) title = text
    }

    fun heading(level: Int, text: String) {
        if (text.isNotBlank()) add(Heading(level, listOf(Text(text))))
    }

    fun heading(level: Int, content: List<Inline>) {
        if (content.isNotEmpty()) add(Heading(level, content))
    }

    fun paragraph(text: String) {
        if (text.isNotBlank()) add(Paragraph(listOf(Text(text))))
    }

    fun paragraph(content: List<Inline>) {
        if (content.isNotEmpty()) add(Paragraph(content))
    }

    fun code(code: String, language: String? = null) {
        add(CodeBlock(code, language))
    }

    fun comment(text: String) {
        add(HtmlComment(text))
    }

    fun raw(text: String) {
        if (text.isNotBlank()) add(RawBlock(text))
    }

    fun bulletList(items: List<String>) {
        if (items.isEmpty()) return
        add(ListBlock(ordered = false, items = items.map { ListItem(listOf(Paragraph(listOf(Text(it))))) }))
    }

    fun table(
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

    fun build(): Document =
        Document(
            blocks = blocks.toList(),
            title = title,
            metadata = metadata.toMap(),
            assets = assets.toList(),
        )
}

/** Builds a list of inlines without repeating `listOf(...)` wrappers in parsers. */
fun inlines(block: InlineBuilder.() -> Unit): List<Inline> = InlineBuilder().apply(block).build()

class InlineBuilder {
    private val items = mutableListOf<Inline>()

    fun text(value: String) {
        if (value.isNotEmpty()) items += Text(value)
    }

    fun strong(value: String) {
        if (value.isNotEmpty()) items += Strong(listOf(Text(value)))
    }

    fun emphasis(value: String) {
        if (value.isNotEmpty()) items += Emphasis(listOf(Text(value)))
    }

    fun code(value: String) {
        if (value.isNotEmpty()) items += CodeSpan(value)
    }

    fun link(text: String, url: String) {
        items += Link(listOf(Text(text)), url)
    }

    fun image(alt: String, url: String, assetId: String? = null) {
        items += Image(alt, url, assetId = assetId)
    }

    fun lineBreak() {
        items += LineBreak
    }

    operator fun plusAssign(inline: Inline) {
        items += inline
    }

    operator fun plusAssign(inlines: List<Inline>) {
        items += inlines
    }

    fun build(): List<Inline> = items.toList()
}

/** Wraps [content] in the emphasis combination described by the flags. */
fun styled(content: List<Inline>, bold: Boolean, italic: Boolean, strike: Boolean = false): List<Inline> {
    var result = content
    if (strike) result = listOf(Strikethrough(result))
    if (italic) result = listOf(Emphasis(result))
    if (bold) result = listOf(Strong(result))
    return result
}
