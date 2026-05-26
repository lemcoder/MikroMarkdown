package io.github.lemcoder.mikromarkdown.utils

import android.webkit.MimeTypeMap
import io.github.lemcoder.mikromarkdown.StreamInfo
import java.io.File

object AndroidMimeDetector {
    fun detect(path: String): StreamInfo {
        val file = File(path)
        val extension = file.extension.lowercase().ifEmpty { null }
        val mimetype = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return StreamInfo(
            mimetype = mimetype,
            extension = extension,
            filename = file.name,
            localPath = path,
        )
    }
}
