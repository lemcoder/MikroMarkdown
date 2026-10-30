package io.github.lemcoder.mikromarkdown.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Paragraph

/**
 * The document as pages rather than a scroll.
 *
 * The pages are the reader's, not the source's: they follow the font size and the screen, so making the text larger
 * makes more of them. A PDF's own page breaks are a property of the paper it was set for, and holding to them would
 * mean showing a phone a page sized for A4.
 */
@Composable
public fun PagedDocumentView(
    document: Document,
    modifier: Modifier = Modifier,
    style: ReaderStyle = ReaderStyle.Default,
) {
    CompositionLocalProvider(LocalReaderStyle provides style) {
        val background = style.colors.background
        BoxWithConstraints(
            modifier = if (background == Color.Unspecified) modifier else modifier.background(background)
        ) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val horizontal =
                style.contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
                    style.contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
            val vertical = style.contentPadding.calculateTopPadding() + style.contentPadding.calculateBottomPadding()

            // Measuring needs the resolved styles, which only a composition can read.
            val measurables = document.measurables()
            val width = with(density) { (maxWidth - horizontal).roundToPx() }
            val height = with(density) { (maxHeight - vertical - PageNumberHeight).roundToPx() }
            val spacing = with(density) { style.blockSpacing.toPx() }

            val pages =
                remember(measurables, width, height, spacing) {
                    paginate(measurables, measurer, width, height, spacing)
                }
            if (pages.isEmpty()) return@BoxWithConstraints

            val pager = rememberPagerState { pages.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                Column(Modifier.fillMaxSize().padding(style.contentPadding)) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(style.blockSpacing),
                    ) {
                        pages[index].items.forEach { item -> PageItemView(item, document) }
                    }
                    Text(
                        text = "${index + 1} / ${pages.size}",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerTextColor().copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageItemView(item: PageItem, document: Document) {
    when (item) {
        // The slice is taken from the same string the measurement ran on, so the styling inside it —
        // a bold run, a link — survives being cut.
        is PageItem.TextRun -> Text(item.text.subSequence(item.start, item.end), style = item.style)
        is PageItem.Whole -> BlockView(item.block, document)
    }
}

/**
 * Reduces the document to what pagination can measure.
 *
 * Text becomes its laid-out string and style. Everything else is placed entire, with a height this can work out: an
 * image's is the size it will draw at, and anything else is given the screen, which puts it on a page of its own.
 * Measuring a table or a list properly means composing it first, which is a subcomposition pass this does not yet make.
 */
@Composable
private fun Document.measurables(): List<Measurable> {
    val density = LocalDensity.current
    val fallback = with(density) { FallbackBlockHeight.toPx() }
    return blocks.map { block ->
        when {
            block is Paragraph && block.soleImageOrNull() == null ->
                Measurable.Text(block.content.annotated(this), MaterialTheme.typography.bodyLarge.scaled(body = true))
            block is Heading -> Measurable.Text(block.content.annotated(this), block.level.headingStyle().scaled())
            else -> Measurable.Whole(block, fallback)
        }
    }
}

private fun Paragraph.soleImageOrNull(): Image? = content.singleOrNull() as? Image

/** What a block gets when its height cannot be worked out: the page, so it sits on one alone. */
private val FallbackBlockHeight = 600.dp

/** Room kept at the foot of every page for its number. */
private val PageNumberHeight = 24.dp
