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
 * documents. Content still wins over the extension, so a mislabelled `.txt` that is really a PDF or an OOXML package is
 * identified correctly.
 *
 * Formats that are plain text with no signature (CSV, JSON, XML, HTML, Markdown) are recognised by extension. Pass
 * [TikaMimeDetector] to `MikroMarkdown` if you need content sniffing for those too.
 */
public object SignatureMimeDetector : MimeDetector {

    private const val SIGNATURE_BYTES = 8

    private val byExtension =
        mapOf(
            "csv" to "text/csv",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "epub" to "application/epub+zip",
            "htm" to "text/html",
            "html" to "text/html",
            "json" to "application/json",
            "md" to "text/markdown",
            "markdown" to "text/markdown",
            "pdf" to "application/pdf",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "txt" to "text/plain",
            "log" to "text/plain",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "xml" to "application/xml",
        )

    /** ZIP-based formats are told apart by extension; the signature only proves it is a package. */
    private val zipExtensions = setOf("docx", "xlsx", "pptx", "epub", "zip")

    override fun detect(path: String): StreamInfo {
        val filename = path.substringAfterLast('/').substringAfterLast('\\')
        val extension = filename.substringAfterLast('.', "").lowercase().ifEmpty { null }
        val signature = readSignature(path)

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
            // Every OOXML container and EPUB is a ZIP; the extension says which one.
            signature.startsWith("PK") ->
                if (extension in zipExtensions) byExtension[extension] ?: "application/zip" else "application/zip"
            // Legacy OLE compound files: .doc/.xls/.ppt, which no converter handles yet.
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

    private fun ByteArray.startsWithBytes(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }
    }
}
