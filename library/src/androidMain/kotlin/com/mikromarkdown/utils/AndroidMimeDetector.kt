package com.mikromarkdown.utils

import android.webkit.MimeTypeMap
import com.mikromarkdown.StreamInfo
import java.io.File

object AndroidMimeDetector {
    fun detect(file: File): StreamInfo {
        val extension = file.extension.lowercase().ifEmpty { null }
        val mimetype = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        return StreamInfo(
            mimetype = mimetype,
            extension = extension,
            filename = file.name,
            localPath = file.absolutePath,
        )
    }
}
