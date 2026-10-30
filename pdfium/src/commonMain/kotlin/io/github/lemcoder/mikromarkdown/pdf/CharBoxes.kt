package io.github.lemcoder.mikromarkdown.pdf

/**
 * Every character's box on a page, in page coordinates, where y grows upward.
 *
 * Packed four floats to a character — left, bottom, right, top — because a page of a paper is thousands of characters
 * and an object apiece would cost more than the reading that fills them. A character pdfium generated rather than read
 * off the page — the line breaks, and the spaces it inferred — has no box, and reports [hasBox] false.
 */
internal class CharBoxes(val size: Int) {
    private val packed = FloatArray(size * 4) { Float.NaN }

    operator fun set(index: Int, box: FloatArray) {
        box.copyInto(packed, index * 4, 0, 4)
    }

    fun hasBox(index: Int): Boolean = !packed[index * 4].isNaN()

    fun left(index: Int): Float = packed[index * 4]

    fun bottom(index: Int): Float = packed[index * 4 + 1]

    fun right(index: Int): Float = packed[index * 4 + 2]

    /** The upper edge, or [Float.MAX_VALUE] for a character with no box, which is then never below anything. */
    fun top(index: Int): Float = if (hasBox(index)) packed[index * 4 + 3] else Float.MAX_VALUE

    /** The boxes of [indices], in that order; an index of -1 has no box. */
    fun select(indices: IntArray): CharBoxes {
        val out = CharBoxes(indices.size)
        for ((at, from) in indices.withIndex()) {
            if (from >= 0) packed.copyInto(out.packed, at * 4, from * 4, from * 4 + 4)
        }
        return out
    }
}
