package io.github.lemcoder.mikromarkdown.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.lemcoder.mikromarkdown.compose.ReaderColors
import io.github.lemcoder.mikromarkdown.compose.ReaderStyle
import kotlin.math.roundToInt

/** How the reader is set up, and what the settings sheet edits. */
data class ReaderSettings(
    val fontScale: Float = 1f,
    val lineHeightScale: Float = 1f,
    val theme: ReaderTheme = ReaderTheme.System,
    val paginated: Boolean = false,
) {
    /** The renderer takes colours and sizes, not preferences; this is where one becomes the other. */
    @Composable
    fun style(): ReaderStyle =
        ReaderStyle(
            fontScale = fontScale,
            lineHeightScale = lineHeightScale,
            colors = theme.colors(),
            contentPadding = PaddingValues(20.dp),
        )
}

/** Reading is a long sitting, and the right background for it is not always the system's. */
enum class ReaderTheme {
    System,
    Light,
    Sepia,
    Dark;

    @Composable
    fun colors(): ReaderColors =
        when (this) {
            // Unspecified means "whatever the Material theme says", which is what System is.
            System -> ReaderColors()
            Light -> ReaderColors(background = Paper, text = Ink)
            Sepia -> ReaderColors(background = SepiaPaper, text = SepiaInk)
            Dark -> ReaderColors(background = NightPaper, text = NightInk)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Reading", style = MaterialTheme.typography.titleMedium)

            Labelled("Text size", "${(settings.fontScale * 100).roundToInt()}%") {
                Slider(
                    value = settings.fontScale,
                    onValueChange = { onChange(settings.copy(fontScale = it)) },
                    valueRange = 0.8f..2.0f,
                    // Twelve steps over that range is roughly one readable notch apiece.
                    steps = 11,
                )
            }

            Labelled("Line spacing", "${(settings.lineHeightScale * 100).roundToInt()}%") {
                Slider(
                    value = settings.lineHeightScale,
                    onValueChange = { onChange(settings.copy(lineHeightScale = it)) },
                    valueRange = 1.0f..1.8f,
                    steps = 7,
                )
            }

            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { onChange(settings.copy(theme = theme)) },
                        label = { Text(theme.name) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pages instead of scrolling", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.paginated,
                    onCheckedChange = { onChange(settings.copy(paginated = it)) },
                )
            }
        }
    }
}

@Composable
private fun Labelled(label: String, value: String, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
        content()
    }
}

/**
 * Keeps the reader's setup across a rotation.
 *
 * Not across a restart: persisting it is a store and a schema, and this app exists to exercise the converters rather
 * than to be a product.
 */
internal val ReaderSettingsSaver: Saver<ReaderSettings, List<Any>> =
    Saver(
        save = { listOf(it.fontScale, it.lineHeightScale, it.theme.name, it.paginated) },
        restore = {
            ReaderSettings(
                fontScale = it[0] as Float,
                lineHeightScale = it[1] as Float,
                theme = ReaderTheme.valueOf(it[2] as String),
                paginated = it[3] as Boolean,
            )
        },
    )
