package io.github.lemcoder.mikromarkdown

import java.io.File
import org.apache.tika.Tika

/**
 * Full content sniffing through Apache Tika's MIME registry.
 *
 * Slower to start than [SignatureMimeDetector] — building the registry costs about 90 ms, more than converting most
 * documents — but it recognises text formats by content rather than by extension.
 */
public object TikaMimeDetector : MimeDetector {
    // Building Tika's MIME registry is expensive; convert(bytes, info) never needs it.
    private val tika by lazy { Tika() }

    override fun detect(path: String): StreamInfo {
        val file = File(path)
        val mimetype =
            try {
                tika.detect(file)
            } catch (_: Exception) {
                null
            }
        val extension = file.extension.lowercase().ifEmpty { null }
        return StreamInfo(
            mimetype = mimetype,
            extension = extension,
            filename = file.name,
            localPath = path,
        )
    }
}
