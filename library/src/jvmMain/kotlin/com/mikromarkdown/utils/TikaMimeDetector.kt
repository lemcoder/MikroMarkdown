package com.mikromarkdown.utils

import com.mikromarkdown.StreamInfo
import org.apache.tika.Tika
import java.io.File

object TikaMimeDetector {
    private val tika = Tika()

    fun detect(file: File): StreamInfo {
        val mimetype = try { tika.detect(file) } catch (_: Exception) { null }
        val extension = file.extension.lowercase().ifEmpty { null }
        return StreamInfo(
            mimetype = mimetype,
            extension = extension,
            filename = file.name,
            localPath = file.absolutePath,
        )
    }
}
