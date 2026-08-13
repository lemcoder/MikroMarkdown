package io.github.lemcoder.mikromarkdown.utils

/**
 * Re-indents JSON without parsing it into values.
 *
 * Tokens are copied verbatim, so numbers keep the spelling they had in the source — `1.50` stays `1.50` rather than
 * becoming `1.5` through a round trip. Anything malformed leaves the input untouched, matching what the converters do
 * when a parse fails.
 */
internal object JsonFormatter {

    private const val INDENT = "    "

    fun prettyPrint(json: String): String {
        val out = StringBuilder(json.length + json.length / 4)
        var depth = 0
        var index = 0
        var afterValue = false

        while (index < json.length) {
            val char = json[index]
            when {
                char.isWhitespace() -> {
                    index++
                    continue
                }

                char == '"' -> {
                    val end = endOfString(json, index) ?: return json
                    out.append(json, index, end)
                    index = end
                    afterValue = true
                    continue
                }

                char == '{' || char == '[' -> {
                    out.append(char)
                    depth++
                    // An empty container stays on one line: "{}" rather than "{\n}".
                    val next = nextMeaningful(json, index + 1)
                    if (next >= 0 && (json[next] == '}' || json[next] == ']')) {
                        out.append(json[next])
                        depth--
                        index = next + 1
                        afterValue = true
                        continue
                    }
                    newLine(out, depth)
                }

                char == '}' || char == ']' -> {
                    depth--
                    newLine(out, depth)
                    out.append(char)
                    afterValue = true
                }

                char == ',' -> {
                    out.append(char)
                    newLine(out, depth)
                    afterValue = false
                }

                char == ':' -> out.append(": ")

                else -> {
                    // A bare literal: number, true, false or null.
                    val end = endOfLiteral(json, index)
                    out.append(json, index, end)
                    index = end
                    afterValue = true
                    continue
                }
            }
            index++
        }

        return if (afterValue) out.toString() else json
    }

    private fun newLine(out: StringBuilder, depth: Int) {
        out.append('\n')
        repeat(depth) { out.append(INDENT) }
    }

    /** Index just past the closing quote, honouring backslash escapes. */
    private fun endOfString(json: String, start: Int): Int? {
        var index = start + 1
        while (index < json.length) {
            when (json[index]) {
                '\\' -> index++
                '"' -> return index + 1
            }
            index++
        }
        return null
    }

    private fun endOfLiteral(json: String, start: Int): Int {
        var index = start
        while (index < json.length && !json[index].isWhitespace() && json[index] !in STRUCTURAL) index++
        return if (index == start) index + 1 else index
    }

    /** Index of the next non-space character, or -1. Returning the index avoids allocating a pair. */
    private fun nextMeaningful(json: String, from: Int): Int {
        for (index in from until json.length) {
            if (!json[index].isWhitespace()) return index
        }
        return -1
    }

    private val STRUCTURAL = charArrayOf('{', '}', '[', ']', ',', ':')
}
