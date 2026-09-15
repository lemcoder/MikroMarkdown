package io.github.lemcoder.mikromarkdown.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified

/**
 * How the reader looks.
 *
 * Passed in rather than read from storage: the renderer stays a function of its arguments, which is what lets a caller
 * preview a setting without saving it and what keeps persistence — a store, a screen, a default — out of a library that
 * only draws. Every colour left [Color.Unspecified] comes from the Material theme instead, so a caller who wants only a
 * larger font says only that.
 */
public data class ReaderStyle(
    /** Multiplies every text size. 1.0 is the theme's own. */
    val fontScale: Float = 1f,
    /** Multiplies line height on body text, which is what makes long prose easier or denser. */
    val lineHeightScale: Float = 1f,
    val blockSpacing: Dp = 12.dp,
    val contentPadding: PaddingValues = PaddingValues(16.dp),
    /**
     * Widest the text is allowed to run.
     *
     * A line that crosses a tablet is hard to read back to; unspecified leaves it to the container.
     */
    val maxContentWidth: Dp = Dp.Unspecified,
    val colors: ReaderColors = ReaderColors(),
) {
    public companion object {
        public val Default: ReaderStyle = ReaderStyle()
    }
}

/** Reader colours. Anything unspecified follows the Material theme. */
public data class ReaderColors(
    val background: Color = Color.Unspecified,
    val text: Color = Color.Unspecified,
    val link: Color = Color.Unspecified,
    val codeBackground: Color = Color.Unspecified,
    val quoteRule: Color = Color.Unspecified,
)

/**
 * The style in force.
 *
 * A composition local rather than a parameter on every composable: the block and inline layers are recursive — a quote
 * holds blocks, a list item holds blocks — and threading style through each of them adds a parameter to the whole tree
 * to say nothing new.
 */
internal val LocalReaderStyle = staticCompositionLocalOf { ReaderStyle.Default }

@Composable
@ReadOnlyComposable
internal fun readerTextColor(): Color =
    LocalReaderStyle.current.colors.text.takeOrElse { MaterialTheme.colorScheme.onSurface }

@Composable
@ReadOnlyComposable
internal fun readerLinkColor(): Color =
    LocalReaderStyle.current.colors.link.takeOrElse { MaterialTheme.colorScheme.primary }

@Composable
@ReadOnlyComposable
internal fun readerCodeBackground(): Color =
    LocalReaderStyle.current.colors.codeBackground.takeOrElse { MaterialTheme.colorScheme.surfaceVariant }

@Composable
@ReadOnlyComposable
internal fun readerQuoteRule(): Color =
    LocalReaderStyle.current.colors.quoteRule.takeOrElse { MaterialTheme.colorScheme.outlineVariant }

/** Applies the reader's scaling and colour to a style from the theme. */
@Composable
@ReadOnlyComposable
internal fun TextStyle.scaled(body: Boolean = false): TextStyle {
    val style = LocalReaderStyle.current
    return copy(
        color = if (color.isSpecified) color else readerTextColor(),
        fontSize = fontSize.scaleBy(style.fontScale),
        // Headings set their own leading; stretching it too makes the gaps look like mistakes.
        lineHeight = lineHeight.scaleBy(style.fontScale * if (body) style.lineHeightScale else 1f),
    )
}

private fun TextUnit.scaleBy(factor: Float): TextUnit = if (isUnspecified) this else this * factor

private val Color.isSpecified: Boolean
    get() = this != Color.Unspecified
