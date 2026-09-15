package io.github.lemcoder.mikromarkdown.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.MikroMarkdownException
import io.github.lemcoder.mikromarkdown.StreamInfo
import io.github.lemcoder.mikromarkdown.compose.DocumentView
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.pdf.PdfiumConverter
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Launched with a document from elsewhere, or launched empty and waiting for the picker.
        val opened = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent { MaterialTheme { ReaderScreen(opened) } }
    }
}

/** What the screen is showing: nothing yet, working, a document, or why there is no document. */
private sealed interface ReaderState {
    data object Empty : ReaderState

    data object Loading : ReaderState

    data class Loaded(val name: String, val document: Document) : ReaderState

    data class Failed(val name: String, val message: String) : ReaderState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(opened: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // One instance: registering the converters is the expensive part, and it is the same every time.
    // PdfiumConverter is not in the factory's list — PDF costs a native library, so a caller opts in.
    val mikroMarkdown = remember { MikroMarkdown().apply { register(PdfiumConverter()) } }
    var state: ReaderState by remember { mutableStateOf(ReaderState.Empty) }
    var settings by rememberSaveable(stateSaver = ReaderSettingsSaver) { mutableStateOf(ReaderSettings()) }
    var settingsOpen by remember { mutableStateOf(false) }

    fun load(uri: Uri) {
        val name = context.displayName(uri)
        state = ReaderState.Loading
        scope.launch {
            state =
                // Reading and converting are both blocking, and a large document is not quick.
                withContext(Dispatchers.IO) {
                    try {
                        val bytes =
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: return@withContext ReaderState.Failed(name, "Could not open the file")
                        val info = StreamInfo(extension = name.substringAfterLast('.', "").lowercase(), filename = name)
                        ReaderState.Loaded(name, mikroMarkdown.parse(bytes, info))
                    } catch (e: MikroMarkdownException) {
                        // An unsupported format, or a converter that failed on the content.
                        ReaderState.Failed(name, e.message ?: e::class.simpleName.orEmpty())
                    } catch (e: IOException) {
                        ReaderState.Failed(name, "Could not read the file: ${e.message}")
                    } catch (e: SecurityException) {
                        // A URI from another app that was never granted, or whose grant expired
                        // with the task that carried it. Nothing here can recover it, and dying
                        // is not the answer either.
                        ReaderState.Failed(name, "No permission to read this document")
                    }
                }
        }
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { load(it) } }

    // An intent brings its own document; the picker's callback and this take the same path.
    LaunchedEffect(opened) { opened?.let { load(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? ReaderState.Loaded)?.name ?: "MikroMarkdown") },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) { Text("Aa") }
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Open") }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val current = state) {
                ReaderState.Empty -> Hint("Open a document to read it.")
                ReaderState.Loading -> CircularProgressIndicator()
                is ReaderState.Loaded -> DocumentView(current.document, Modifier.fillMaxSize(), settings.style())
                is ReaderState.Failed -> Failure(current)
            }
        }
    }

    if (settingsOpen) {
        ReaderSettingsSheet(settings, onChange = { settings = it }, onDismiss = { settingsOpen = false })
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
}

@Composable
private fun Failure(state: ReaderState.Failed) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(state.name, style = MaterialTheme.typography.titleMedium)
        Text(state.message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The name the picker shows, which is also where the extension the converters select on comes from. */
private fun android.content.Context.displayName(uri: Uri): String =
    try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    } ?: uri.lastPathSegment.orEmpty()
