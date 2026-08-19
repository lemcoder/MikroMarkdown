package io.github.lemcoder.mikromarkdown

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Detects a format from the file's leading bytes, falling back to its extension.
 *
 * This is the default because it costs microseconds: it reads a handful of bytes and consults a fixed table, where a
 * full MIME registry (Tika) spends ~90 ms building itself on first use — more than the entire conversion for most
 * documents. Content still wins over the extension, so a mislabelled `.txt` that is really a PDF or a ZIP package is
 * identified correctly.
 *
 * Formats that are plain text with no signature (CSV, JSON, XML, HTML, Markdown) are recognised by extension. Content
 * sniffing for those is a [MimeDetector] away — it is a `fun interface`, and the pipeline takes any implementation.
 */
public object SignatureMimeDetector : MimeDetector {

    private const val SIGNATURE_BYTES = 8

    private val byExtension =
        mapOf(
            "csv" to "text/csv",
            "epub" to "application/epub+zip",
            "htm" to "text/html",
            "html" to "text/html",
            "json" to "application/json",
            "md" to "text/markdown",
            "markdown" to "text/markdown",
            "pdf" to "application/pdf",
            "txt" to "text/plain",
            "log" to "text/plain",
            "xml" to "application/xml",
        )

    override fun detect(path: String): StreamInfo = describe(path, readSignature(path))

    override fun detect(path: String, bytes: ByteArray): StreamInfo =
        describe(path, if (bytes.size <= SIGNATURE_BYTES) bytes else bytes.copyOf(SIGNATURE_BYTES))

    private fun describe(path: String, signature: ByteArray): StreamInfo {
        val filename = path.substringAfterLast('/').substringAfterLast('\\')
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }

        return StreamInfo(
            mimetype = mimetypeOf(signature, extension),
            extension = extension,
            filename = filename,
            localPath = path,
        )
    }

    private fun mimetypeOf(signature: ByteArray, extension: String?): String? =
        when {
            signature.startsWith("%PDF") -> "application/pdf"
            // EPUB and every OOXML container is a ZIP; the extension says which one, and only EPUB is ours. The rest
            // are reported as the package they are, which is what leaves the caller a message naming a real format.
            signature.startsWith("PK") -> if (extension == "epub") "application/epub+zip" else "application/zip"
            // Legacy OLE compound files: .doc/.xls/.ppt, which no converter handles. Naming the container is what
            // leaves the caller a message about a format rather than "No converter found for: unknown"; the OOXML
            // mimetypes are not restored with it, because their extension already reaches the caller in [StreamInfo].
            signature.startsWithBytes(0xD0, 0xCF, 0x11, 0xE0) -> "application/x-ole-storage"
            else -> byExtension[extension]
        }

    private fun readSignature(path: String): ByteArray =
        try {
            SystemFileSystem.source(Path(path)).buffered().use { source ->
                // Files shorter than the signature are read whole rather than failing.
                if (source.request(SIGNATURE_BYTES.toLong())) source.readByteArray(SIGNATURE_BYTES)
                else source.readByteArray()
            }
        } catch (_: Exception) {
            ByteArray(0)
        }

    private fun ByteArray.startsWith(prefix: String): Boolean {
        if (size < prefix.length) return false
        return prefix.indices.all { this[it].toInt().toChar() == prefix[it] }
    }

    /** Signatures outside ASCII are spelled as the bytes they are. */
    private fun ByteArray.startsWithBytes(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it].toByte() }
    }
}
