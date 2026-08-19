package io.github.lemcoder.mikromarkdown.utils

import korlibs.io.compression.deflate.Deflate
import korlibs.io.compression.uncompress

/**
 * Reads a ZIP container held in memory.
 *
 * korlibs supplies the inflate and nothing else, and neither kotlinx-io nor okio reads archives, so the container
 * itself is parsed here: find the end-of-central-directory record, walk the directory, and follow each entry to its
 * local header. Enough of the format for EPUB and, later, OOXML.
 */
internal class ZipArchive private constructor(private val source: ByteArray, private val entries: Map<String, Entry>) {

    val names: Set<String>
        get() = entries.keys

    /** The entry's bytes, inflated if it was deflated, or null if it is absent or unreadable. */
    fun read(name: String): ByteArray? {
        val entry = entries[name] ?: return null
        val start = dataStart(entry) ?: return null
        if (start + entry.compressedSize > source.size) return null

        val raw = source.copyOfRange(start, start + entry.compressedSize)
        return when (entry.method) {
            STORED -> raw
            DEFLATED ->
                try {
                    raw.uncompress(Deflate)
                } catch (_: Exception) {
                    null
                }
            // Any other method — bzip2, lzma, encrypted — is not something we can read.
            else -> null
        }
    }

    /** The entry decoded as UTF-8, which every XML and XHTML part of an EPUB is. */
    fun readText(name: String): String? = read(name)?.decodeToString()

    /**
     * Where an entry's bytes begin.
     *
     * The central directory records where the local header is, but the local header repeats the name and extra field
     * with lengths of its own, so the data offset can only be computed from there.
     */
    private fun dataStart(entry: Entry): Int? {
        val header = entry.localHeaderOffset
        if (header + LOCAL_HEADER_MINIMUM > source.size) return null
        if (source.readInt(header) != LOCAL_HEADER_SIGNATURE) return null
        val nameLength = source.readShort(header + 26)
        val extraLength = source.readShort(header + 28)
        return header + LOCAL_HEADER_MINIMUM + nameLength + extraLength
    }

    private class Entry(val localHeaderOffset: Int, val compressedSize: Int, val method: Int)

    companion object {
        private const val STORED = 0
        private const val DEFLATED = 8
        private const val END_OF_DIRECTORY_SIGNATURE = 0x06054b50
        private const val DIRECTORY_ENTRY_SIGNATURE = 0x02014b50
        private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
        private const val LOCAL_HEADER_MINIMUM = 30
        private const val DIRECTORY_ENTRY_MINIMUM = 46
        private const val END_OF_DIRECTORY_MINIMUM = 22

        /** The comment that may follow the end record, and so how far back it can hide. */
        private const val MAX_COMMENT = 0xFFFF

        fun open(bytes: ByteArray): ZipArchive? {
            val end = findEndOfDirectory(bytes) ?: return null
            val count = bytes.readShort(end + 10)
            var offset = bytes.readInt(end + 16)

            val entries = LinkedHashMap<String, Entry>(count)
            repeat(count) {
                if (offset + DIRECTORY_ENTRY_MINIMUM > bytes.size) return@repeat
                if (bytes.readInt(offset) != DIRECTORY_ENTRY_SIGNATURE) return@repeat

                val method = bytes.readShort(offset + 10)
                val compressedSize = bytes.readInt(offset + 20)
                val nameLength = bytes.readShort(offset + 28)
                val extraLength = bytes.readShort(offset + 30)
                val commentLength = bytes.readShort(offset + 32)
                val localHeaderOffset = bytes.readInt(offset + 42)

                val nameStart = offset + DIRECTORY_ENTRY_MINIMUM
                if (nameStart + nameLength > bytes.size) return@repeat
                val name = bytes.decodeToString(nameStart, nameStart + nameLength)
                // Directories carry no data and end in a separator.
                if (!name.endsWith("/")) {
                    entries[name] = Entry(localHeaderOffset, compressedSize, method)
                }
                offset = nameStart + nameLength + extraLength + commentLength
            }

            return ZipArchive(bytes, entries)
        }

        /** The end record sits last, unless a comment follows it, so the search runs backwards. */
        private fun findEndOfDirectory(bytes: ByteArray): Int? {
            if (bytes.size < END_OF_DIRECTORY_MINIMUM) return null
            val earliest = maxOf(0, bytes.size - END_OF_DIRECTORY_MINIMUM - MAX_COMMENT)
            for (offset in bytes.size - END_OF_DIRECTORY_MINIMUM downTo earliest) {
                if (bytes.readInt(offset) == END_OF_DIRECTORY_SIGNATURE) return offset
            }
            return null
        }

        private fun ByteArray.readShort(at: Int): Int =
            (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

        private fun ByteArray.readInt(at: Int): Int =
            (this[at].toInt() and 0xFF) or
                ((this[at + 1].toInt() and 0xFF) shl 8) or
                ((this[at + 2].toInt() and 0xFF) shl 16) or
                ((this[at + 3].toInt() and 0xFF) shl 24)
    }
}
