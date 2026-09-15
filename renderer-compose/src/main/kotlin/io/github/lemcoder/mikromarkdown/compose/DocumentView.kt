package io.github.lemcoder.mikromarkdown.compose

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment as LayoutAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lemcoder.mikromarkdown.model.Alignment as CellAlignment
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.BlockQuote
import io.github.lemcoder.mikromarkdown.model.CodeBlock
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Image as ModelImage
import io.github.lemcoder.mikromarkdown.model.ListBlock
import io.github.lemcoder.mikromarkdown.model.ListItem
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.RawBlock
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell
import io.github.lemcoder.mikromarkdown.model.ThematicBreak

/**
 * Renders a [Document] on screen.
 *
 * A sibling of `MarkdownRenderer` rather than a consumer of it: both take the same model, so nothing here parses
 * Markdown and nothing re-parses what the Markdown renderer produced. Every block type in the model is handled, and a
 * `when` without an `else` is what keeps that true as the model grows.
 */
@Composable
public fun DocumentView(
    document: Document,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        document.title?.let { title -> item { Text(title, style = MaterialTheme.typography.headlineMedium) } }
        items(document.blocks) { block -> BlockView(block, document) }
    }
}

/** One block. Nested blocks — quotes, list items — come back through here. */
@Composable
internal fun BlockView(block: Block, document: Document, modifier: Modifier = Modifier) {
    when (block) {
        is Heading -> Text(block.content.annotated(document), modifier, style = block.level.headingStyle())
        // A paragraph that is only an image is a figure, and a figure is drawn rather than described.
        is Paragraph ->
            when (val figure = block.soleImage()) {
                null -> Text(block.content.annotated(document), modifier, style = MaterialTheme.typography.bodyLarge)
                else -> ImageView(figure, document, modifier)
            }
        is CodeBlock -> CodeBlockView(block, modifier)
        is BlockQuote -> BlockQuoteView(block, document, modifier)
        is ListBlock -> ListBlockView(block, document, modifier)
        is Table -> TableView(block, document, modifier)
        ThematicBreak -> HorizontalDivider(modifier.padding(vertical = 8.dp))
        // Already-rendered Markdown or HTML: the model's escape hatch, and there is nothing to lay
        // out but the text itself.
        is RawBlock -> Text(block.text, modifier, style = MaterialTheme.typography.bodySmall)
    }
}

/** The one image a paragraph holds on its own, if that is all it holds. */
private fun Paragraph.soleImage(): ModelImage? = content.singleOrNull() as? ModelImage

/**
 * An image the document carries.
 *
 * Only assets are drawn. A plain URL is a reference to somewhere else, and fetching it would put a network stack behind
 * a function whose whole job is laying out a model that is already in memory — the alt text stands in until a caller
 * supplies an image loader.
 */
@Composable
private fun ImageView(image: ModelImage, document: Document, modifier: Modifier) {
    val bitmap = rememberAssetBitmap(image.assetId, document)
    if (bitmap == null) {
        Text(image.alt.ifBlank { image.url }, modifier, style = MaterialTheme.typography.bodyMedium)
        return
    }
    // Never enlarge. A figure wider than the screen is scaled down to fit; an icon keeps its own
    // size, because blowing a 100px tick up to the full width of a phone is not what the page meant.
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = image.alt.ifBlank { null },
        modifier = modifier.sizeIn(maxWidth = with(LocalDensity.current) { bitmap.width.toDp() }).fillMaxWidth(),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Decodes an asset once per composition rather than on every frame.
 *
 * Bytes in the model are PNG or JPEG, which is what [BitmapFactory] takes; a stream it cannot decode returns null and
 * the alt text takes over.
 */
@Composable
private fun rememberAssetBitmap(assetId: String?, document: Document): ImageBitmap? =
    remember(assetId, document) {
        val bytes = document.assets.firstOrNull { it.id == assetId }?.bytes ?: return@remember null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }

@Composable
private fun CodeBlockView(block: CodeBlock, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
    ) {
        // A code block sets its own line breaks, so it scrolls sideways rather than wrapping.
        Text(
            text = block.code,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Monospace),
            softWrap = false,
        )
    }
}

@Composable
private fun BlockQuoteView(block: BlockQuote, document: Document, modifier: Modifier) {
    // IntrinsicSize.Min gives the row the height of its content, which is what lets the rule beside
    // the quote run the full height of it rather than collapsing to nothing.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { BlockView(it, document) }
        }
    }
}

@Composable
private fun ListBlockView(block: ListBlock, document: Document, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (block.loose) 8.dp else 2.dp),
    ) {
        block.items.forEachIndexed { index, item -> ListItemView(item, block.marker(index), document) }
    }
}

/** A hanging indent: the marker keeps its column while the content wraps under itself. */
@Composable
private fun ListItemView(item: ListItem, marker: String, document: Document) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = marker,
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
        Column(
            modifier = Modifier.padding(start = 8.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item.blocks.forEach { BlockView(it, document) }
        }
    }
}

private fun ListBlock.marker(index: Int): String =
    when {
        // A task list is a bullet list whose items carry a checked state.
        items[index].checked == true -> "☑"
        items[index].checked == false -> "☐"
        ordered -> "${start + index}."
        else -> "•"
    }

/**
 * A table, measured rather than laid out by a grid primitive.
 *
 * Compose has no table, and a `Row` of `Column`s cannot align rows whose cells differ in height. The columns are sized
 * from the widest cell in each and the whole thing scrolls sideways, which is what a wide table needs on a phone either
 * way. Column spans are honoured by width; row spans are not, because nothing in the model's producers emits them yet.
 */
@Composable
private fun TableView(table: Table, document: Document, modifier: Modifier) {
    val columns = table.columnCount()
    if (columns == 0) return

    Column(modifier.horizontalScroll(rememberScrollState())) {
        if (table.header.isNotEmpty()) {
            TableRow(table.header, columns, table.alignments, document, header = true)
            HorizontalDivider()
        }
        table.rows.forEach { row -> TableRow(row, columns, table.alignments, document, header = false) }
        if (table.caption.isNotEmpty()) {
            Text(
                text = table.caption.annotated(document),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<TableCell>,
    columns: Int,
    alignments: List<CellAlignment>,
    document: Document,
    header: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = LayoutAlignment.Top) {
        var column = 0
        cells.forEach { cell ->
            val width = CellWidth * cell.colSpan
            Box(Modifier.width(width).padding(6.dp)) {
                Text(
                    text = cell.content.annotated(document),
                    style = if (header) MaterialTheme.typography.titleSmall else LocalTextStyle.current,
                    textAlign = alignments.getOrNull(column)?.textAlign(),
                )
            }
            column += cell.colSpan
        }
        // Short rows are padded out, so the columns of the rows below still line up.
        repeat((columns - column).coerceAtLeast(0)) { Box(Modifier.width(CellWidth)) }
    }
}

/** The widest row decides, because a header may declare fewer columns than a body row uses. */
private fun Table.columnCount(): Int =
    maxOf(header.sumOf { it.colSpan }, rows.maxOfOrNull { row -> row.sumOf { it.colSpan } } ?: 0)

private fun CellAlignment.textAlign(): TextAlign? =
    when (this) {
        CellAlignment.NONE -> null
        CellAlignment.LEFT -> TextAlign.Start
        CellAlignment.CENTER -> TextAlign.Center
        CellAlignment.RIGHT -> TextAlign.End
    }

private val CellWidth = 160.dp
