package io.github.lemcoder.mikromarkdown.utils

import io.github.lemcoder.mikromarkdown.model.Block
import io.github.lemcoder.mikromarkdown.model.Paragraph
import io.github.lemcoder.mikromarkdown.model.Text

/**
 * Turns extracted plain text (PDF pages, speaker notes, …) into paragraph blocks.
 *
 * Blank lines separate paragraphs; soft-wrapped lines inside a paragraph are rejoined,
 * so the Markdown does not inherit the source layout's line breaks.
 */
fun plainTextBlocks(text: String, reflow: Boolean = true): List<Block> {
    // Form feeds mark PDF page breaks; treat them as paragraph boundaries.
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u000C', '\n')
    val vocabulary = if (reflow) wordsIn(normalized) else emptySet()
    val paragraphs = mutableListOf<String>()

    for (chunk in normalized.split(Regex("\n[ \t]*\n"))) {
        val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) continue
        val joined = if (reflow) joinWrappedLines(lines, vocabulary) else lines.joinToString("\n")
        // A paragraph ending mid-word means the break was a layout artifact, not a real one.
        val previous = paragraphs.lastOrNull()
        if (reflow && previous != null && previous.endsWithWordBreak()) {
            paragraphs[paragraphs.lastIndex] = joinWrappedLines(listOf(previous, joined), vocabulary)
        } else {
            paragraphs += joined
        }
    }

    return paragraphs.map { Paragraph(listOf(Text(it))) }
}

private fun String.endsWithWordBreak(): Boolean =
    endsWith("-") && length > 1 && this[length - 2].isLetter()

private val WORD = Regex("[\\p{L}]{2,}")

/** Words the document uses on their own; the de-hyphenation heuristic consults this. */
private fun wordsIn(text: String): Set<String> =
    WORD.findAll(text).map { it.value.lowercase() }.toSet()

/**
 * Rejoins soft-wrapped lines.
 *
 * A trailing hyphen is dropped only when it looks like a wrap artifact: if both fragments are
 * words the document uses elsewhere on their own (`conversation-` + `centric`), the hyphen is a
 * real compound and stays.
 */
private fun joinWrappedLines(lines: List<String>, vocabulary: Set<String>): String {
    val sb = StringBuilder()
    for ((index, line) in lines.withIndex()) {
        if (index == 0) {
            sb.append(line)
            continue
        }
        val head = sb.lastFragment()
        val tail = line.takeWhile { it.isLetter() }.lowercase()
        val hyphenated = sb.isNotEmpty() && sb.last() == '-' && head.isNotEmpty() && tail.isNotEmpty()
        val realCompound = hyphenated && head in vocabulary && tail in vocabulary

        when {
            hyphenated && !realCompound -> {
                sb.setLength(sb.length - 1)
                sb.append(line)
            }

            hyphenated -> sb.append(line)
            else -> sb.append(' ').append(line)
        }
    }
    return sb.toString()
}

/** The word immediately before a trailing hyphen, lowercased. */
private fun StringBuilder.lastFragment(): String {
    if (isEmpty() || last() != '-') return ""
    var start = length - 1
    while (start > 0 && this[start - 1].isLetter()) start--
    return substring(start, length - 1).lowercase()
}
