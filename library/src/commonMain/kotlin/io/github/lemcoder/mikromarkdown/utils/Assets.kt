package io.github.lemcoder.mikromarkdown.utils

import io.github.lemcoder.mikromarkdown.model.Asset
import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.BlockQuote
import io.github.lemcoder.mikromarkdown.model.Document
import io.github.lemcoder.mikromarkdown.model.Emphasis
import io.github.lemcoder.mikromarkdown.model.Heading
import io.github.lemcoder.mikromarkdown.model.Image
import io.github.lemcoder.mikromarkdown.model.Inline
import io.github.lemcoder.mikromarkdown.model.Link
import io.github.lemcoder.mikromarkdown.model.ListBlock
import io.github.lemcoder.mikromarkdown.model.ListItem
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.Strikethrough
import io.github.lemcoder.mikromarkdown.model.Strong
import io.github.lemcoder.mikromarkdown.model.Table
import io.github.lemcoder.mikromarkdown.model.TableCell

/**
 * Turns the images a document points at into images it carries.
 *
 * A pass over the finished model rather than a hook inside the HTML walker: the walker is a chain of private functions
 * that would each have to grow a parameter, and an image is identified by its `src` no matter which of them produced
 * it. So each converter says only how a `src` becomes bytes — a zip entry for EPUB, a decoded `data:` URI for HTML —
 * and the rewriting is shared.
 *
 * An image whose [resolve] returns null is left exactly as it was, which is what keeps a remote URL a remote URL.
 */
internal fun Document.withAssets(resolve: (url: String) -> Asset?): Document {
    val assets = LinkedHashMap<String, Asset>()

    fun resolveOnce(url: String): Asset? {
        // The same image used twice is one asset: a chapter that repeats a figure, or a cover that
        // also appears in the text, should not carry the bytes twice.
        assets[url]?.let {
            return it
        }
        val asset = resolve(url) ?: return null
        assets[url] = asset
        return asset
    }

    val rewritten = mapImages { image -> resolveOnce(image.url)?.let { image.copy(assetId = it.id) } ?: image }
    if (assets.isEmpty()) return this
    return rewritten.copy(assets = this.assets + assets.values)
}

/** Rewrites every image in the document, wherever it is nested. */
internal fun Document.mapImages(transform: (Image) -> Image): Document =
    copy(blocks = blocks.map { it.mapImages(transform) })

private fun Block.mapImages(transform: (Image) -> Image): Block =
    when (this) {
        is Paragraph -> copy(content = content.mapImages(transform))
        is Heading -> copy(content = content.mapImages(transform))
        is BlockQuote -> copy(blocks = blocks.map { it.mapImages(transform) })
        is ListBlock -> copy(items = items.map { ListItem(it.blocks.map { b -> b.mapImages(transform) }, it.checked) })
        is Table ->
            copy(
                header = header.map { it.mapImages(transform) },
                rows = rows.map { row -> row.map { it.mapImages(transform) } },
                caption = caption.mapImages(transform),
            )
        // Code, raw passthrough and thematic breaks hold no inlines to rewrite.
        else -> this
    }

private fun TableCell.mapImages(transform: (Image) -> Image): TableCell =
    TableCell(content.mapImages(transform), colSpan, rowSpan)

private fun List<Inline>.mapImages(transform: (Image) -> Image): List<Inline> = map { inline ->
    when (inline) {
        is Image -> transform(inline)
        is Strong -> inline.copy(content = inline.content.mapImages(transform))
        is Emphasis -> inline.copy(content = inline.content.mapImages(transform))
        is Strikethrough -> inline.copy(content = inline.content.mapImages(transform))
        is Link -> inline.copy(content = inline.content.mapImages(transform))
        else -> inline
    }
}

/**
 * Decodes a `data:` URI into an asset.
 *
 * A data URI is already the bytes, spelled as text: leaving it in the model means a multi-megabyte string travels as a
 * URL, and every consumer that wanted the image has to decode it again. Percent-encoded (non-base64) data URIs are left
 * alone — they are text formats in practice, and decoding them is not what an image consumer wants.
 */
internal fun dataUriAsset(url: String, id: String): Asset? {
    if (!url.startsWith("data:")) return null
    val comma = url.indexOf(',').takeIf { it > 0 } ?: return null
    val header = url.substring("data:".length, comma)
    if (!header.endsWith(";base64")) return null
    val mediaType = header.removeSuffix(";base64").ifBlank { "application/octet-stream" }
    val bytes = base64Decode(url.substring(comma + 1)) ?: return null
    return Asset(id = id, mediaType = mediaType, bytes = bytes, name = null)
}

/** Guesses a media type from a file extension, for assets that arrive as bare bytes in a container. */
internal fun mediaTypeForImage(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg",
        "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "bmp" -> "image/bmp"
        "tif",
        "tiff" -> "image/tiff"
        else -> "application/octet-stream"
    }

/** Base64 for a `data:` URI, the other direction from [base64Decode]. */
internal fun base64Encode(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var index = 0
    while (index + 2 < bytes.size) {
        val chunk =
            (bytes[index].toInt() and 0xFF shl 16) or
                (bytes[index + 1].toInt() and 0xFF shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
        out.append(BASE64_ALPHABET[chunk shr 18 and 0x3F])
        out.append(BASE64_ALPHABET[chunk shr 12 and 0x3F])
        out.append(BASE64_ALPHABET[chunk shr 6 and 0x3F])
        out.append(BASE64_ALPHABET[chunk and 0x3F])
        index += 3
    }
    when (bytes.size - index) {
        1 -> {
            val chunk = bytes[index].toInt() and 0xFF shl 16
            out.append(BASE64_ALPHABET[chunk shr 18 and 0x3F])
            out.append(BASE64_ALPHABET[chunk shr 12 and 0x3F])
            out.append("==")
        }
        2 -> {
            val chunk = (bytes[index].toInt() and 0xFF shl 16) or (bytes[index + 1].toInt() and 0xFF shl 8)
            out.append(BASE64_ALPHABET[chunk shr 18 and 0x3F])
            out.append(BASE64_ALPHABET[chunk shr 12 and 0x3F])
            out.append(BASE64_ALPHABET[chunk shr 6 and 0x3F])
            out.append('=')
        }
    }
    return out.toString()
}

/** The file name an asset should take when written beside the Markdown. */
internal fun Asset.fileName(): String {
    val candidate = name?.substringAfterLast('/')?.ifBlank { null } ?: id.substringAfterLast('/')
    val safe = candidate.map { if (it.isLetterOrDigit() || it in "-_.") it else '-' }.joinToString("")
    return if ('.' in safe) safe else safe + extensionFor(mediaType)
}

private fun extensionFor(mediaType: String): String =
    when (mediaType) {
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/bmp" -> ".bmp"
        "image/tiff" -> ".tif"
        else -> ""
    }

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Kotlin's own base64 is still experimental on every target here, and this is the whole of it. */
private fun base64Decode(text: String): ByteArray? {
    val clean = text.filterNot { it == '\n' || it == '\r' || it == '=' || it == ' ' }
    if (clean.isEmpty()) return ByteArray(0)

    val out = ArrayList<Byte>(clean.length * 3 / 4)
    var buffer = 0
    var bits = 0
    for (char in clean) {
        val value = BASE64_ALPHABET.indexOf(char).takeIf { it >= 0 } ?: return null
        buffer = (buffer shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out += ((buffer shr bits) and 0xFF).toByte()
        }
    }
    return out.toByteArray()
}
