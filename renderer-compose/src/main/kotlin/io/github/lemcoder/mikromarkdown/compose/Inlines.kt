package io.github.lemcoder.mikromarkdown.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.github.lemcoder.mikromarkdown.model.CodeSpan
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Emphasis
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Inline
import io.github.lemcoder.mikromarkdown.model.LineBreak
import io.github.lemcoder.mikromarkdown.model.Link
import io.github.lemcoder.mikromarkdown.model.RawInline
import io.github.lemcoder.mikromarkdown.model.Strikethrough
import io.github.lemcoder.mikromarkdown.model.Strong
import io.github.lemcoder.mikromarkdown.model.Text

internal val Monospace = FontFamily.Monospace

/**
 * Flattens inline content into one styled string.
 *
 * Styling nests, so this recurses and lets [buildAnnotatedString] stack the spans: bold inside a link inside emphasis
 * comes out as all three. An image is written as its alt text — a `Text` cannot hold a bitmap, and the block layer is
 * where an image becomes its own element.
 */
@Composable
@ReadOnlyComposable
internal fun List<Inline>.annotated(document: Document): AnnotatedString {
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString { appendInlines(this@annotated, document, linkStyle, codeBackground) }
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<Inline>,
    document: Document,
    linkStyle: SpanStyle,
    codeBackground: androidx.compose.ui.graphics.Color,
) {
    inlines.forEach { inline ->
        when (inline) {
            is Text -> append(inline.value)
            is Strong ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlines(inline.content, document, linkStyle, codeBackground)
                }
            is Emphasis ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlines(inline.content, document, linkStyle, codeBackground)
                }
            is Strikethrough ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlines(inline.content, document, linkStyle, codeBackground)
                }
            is CodeSpan ->
                withStyle(SpanStyle(fontFamily = Monospace, background = codeBackground)) { append(inline.code) }
            is Link ->
                withLink(LinkAnnotation.Url(inline.url, TextLinkStyles(style = linkStyle))) {
                    appendInlines(inline.content, document, linkStyle, codeBackground)
                }
            // The alt text stands in: an image inside a run of text has nowhere to be laid out.
            is Image -> append(inline.alt)
            LineBreak -> append('\n')
            // Raw Markdown or HTML the producer asked to pass through untouched.
            is RawInline -> append(inline.text)
        }
    }
}

/** Markdown levels run 1..6; anything outside that reads as body text rather than disappearing. */
@Composable
@ReadOnlyComposable
internal fun Int.headingStyle(): TextStyle =
    when (this) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        4 -> MaterialTheme.typography.titleLarge
        5 -> MaterialTheme.typography.titleMedium
        6 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }
