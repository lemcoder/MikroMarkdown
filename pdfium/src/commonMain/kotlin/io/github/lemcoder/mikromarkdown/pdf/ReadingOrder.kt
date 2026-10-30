package io.github.lemcoder.mikromarkdown.pdf

/**
 * The page's text in the order a reader takes it, rebuilt from where each piece sits rather than the order the file
 * stores it in.
 *
 * pdfium returns text in content-stream order, which is whatever order the producer wrote it in. A typesetter writes
 * prose top to bottom and that order is right; an OCR layer can write a table column by column, so a contents page
 * arrives as every entry number, then every title, then every page number. The geometry says what the order should be.
 *
 * The unit is a run: pdfium already breaks its text wherever the line changes, so a run is a stretch of one line — a
 * whole line of prose, or one cell of a table. Runs are ordered by cutting the page recursively: down a gutter where
 * there is wide text on both sides, which is a page's text columns, otherwise across the widest horizontal gap. What no
 * cut divides is read as rows, top to bottom and left to right. A gutter beside a narrow column — the entry numbers or
 * page numbers of a contents page — does not qualify, so those stay in their rows.
 *
 * Line breaks are rebuilt too. A row that is a table's — a wide gap inside it, or dot leaders — is a line of its own,
 * as is a row after a gap larger than the leading; any other row joins its paragraph as before.
 *
 * Without boxes for its characters a page is returned as it came.
 */
internal fun PageText.inReadingOrder(): PageText {
    if (boxes.size != text.length) return this
    val runs = runs()
    if (runs.size < 2) return this
    val em =
        runs.map { it.height }.filter { it > 0f }.sorted().let { if (it.isEmpty()) return this else it[it.size / 2] }

    val rows = mutableListOf<Row>()
    val columnStarts = mutableSetOf<Int>()
    Layout(em, rows, columnStarts).cut(runs)

    val out = StringBuilder(text.length + rows.size)
    val origin = ArrayList<Int>(text.length + rows.size)
    for ((index, row) in rows.withIndex()) {
        if (index > 0) {
            val separator = separatorBefore(rows[index - 1], row, index in columnStarts, em)
            for (char in separator) {
                out.append(char)
                origin += -1
            }
        }
        for ((at, run) in row.runs.withIndex()) {
            if (at > 0) {
                out.append(' ')
                origin += -1
            }
            for (char in run.start until run.end) {
                out.append(text[char])
                origin += char
            }
        }
    }

    val newIndex = IntArray(text.length) { -1 }
    for ((at, from) in origin.withIndex()) if (from >= 0) newIndex[from] = at
    val wraps = hyphenWraps.map { newIndex[it] }.filter { it >= 0 }.sorted().toIntArray()
    return PageText(out.toString(), wraps, images, boxes.select(origin.toIntArray()))
}

/** A stretch of one line, and the box around its characters. */
private class Run(val start: Int, val end: Int, val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val height: Float
        get() = top - bottom
}

/** Runs that share a line, left to right. */
private class Row(val runs: List<Run>, val tabular: Boolean) {
    val top: Float = runs.maxOf { it.top }
    val bottom: Float = runs.minOf { it.bottom }
}

/**
 * The page's runs: its text cut at every line break pdfium wrote, and after every hyphen it removed at one.
 *
 * pdfium writes no break at a hyphenated wrap — `con` and `firming` arrive adjacent, the marker between — so the marker
 * ends a run too, or one run would span two lines. Whitespace is trimmed off each end, and a run with no character
 * pdfium could place is dropped: there is nowhere to put it.
 */
private fun PageText.runs(): List<Run> {
    val runs = mutableListOf<Run>()
    var start = 0
    for (index in 0..text.length) {
        val char = text.getOrNull(index)
        val endsBefore = char == null || char == '\r' || char == '\n'
        val endsAfter = char == HYPHEN_MARKER
        if (!endsBefore && !endsAfter) continue
        val end = if (endsAfter) index + 1 else index
        run(start, end)?.let { runs += it }
        start = end + if (endsBefore) 1 else 0
    }
    return runs
}

private fun PageText.run(from: Int, to: Int): Run? {
    var start = from
    var end = to
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    if (start == end) return null

    var left = Float.MAX_VALUE
    var bottom = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var top = -Float.MAX_VALUE
    for (index in start until end) {
        if (!boxes.hasBox(index)) continue
        left = minOf(left, boxes.left(index))
        bottom = minOf(bottom, boxes.bottom(index))
        right = maxOf(right, boxes.right(index))
        top = maxOf(top, boxes.top(index))
    }
    return if (left > right) null else Run(start, end, left, bottom, right, top)
}

/** Recursive cuts over the page's runs, writing rows in reading order. */
private class Layout(
    private val em: Float,
    private val rows: MutableList<Row>,
    /** Rows that begin a new text column, where the paragraph has certainly ended. */
    private val columnStarts: MutableSet<Int>,
) {
    fun cut(region: List<Run>) {
        if (region.size > 1) {
            columns(region)?.let { (left, right) ->
                cut(left)
                columnStarts += rows.size
                cut(right)
                return
            }
            bands(region)?.let { (upper, lower) ->
                cut(upper)
                cut(lower)
                return
            }
        }
        rows += rowsOf(region)
    }

    /**
     * The region split down its widest gutter that has wide text on both sides, or null.
     *
     * Both sides must hold a real share of the region and more than a stray run or two; a narrow side is a column of a
     * table, whose cells belong to the rows they sit in.
     */
    private fun columns(region: List<Run>): Pair<List<Run>, List<Run>>? {
        val sorted = region.sortedBy { it.left }
        val regionLeft = sorted.first().left
        val regionRight = region.maxOf { it.right }
        val width = regionRight - regionLeft
        var best: Pair<Int, Float>? = null
        var reach = sorted.first().right
        for (index in 1 until sorted.size) {
            val gap = sorted[index].left - reach
            if (gap >= GUTTER * em) {
                val leftShare = (reach - regionLeft) / width
                val rightShare = (regionRight - sorted[index].left) / width
                val wide = leftShare >= MIN_COLUMN_SHARE && rightShare >= MIN_COLUMN_SHARE
                val populated = index >= MIN_COLUMN_RUNS && sorted.size - index >= MIN_COLUMN_RUNS
                if (wide && populated && gap > (best?.second ?: 0f)) best = index to gap
            }
            reach = maxOf(reach, sorted[index].right)
        }
        val split = best?.first ?: return null
        return sorted.subList(0, split) to sorted.subList(split, sorted.size)
    }

    /** The region split across its widest horizontal gap, or null where every run overlaps the next. */
    private fun bands(region: List<Run>): Pair<List<Run>, List<Run>>? {
        val sorted = region.sortedByDescending { it.top }
        var best: Pair<Int, Float>? = null
        var floor = sorted.first().bottom
        for (index in 1 until sorted.size) {
            val gap = floor - sorted[index].top
            if (gap > 0f && gap > (best?.second ?: 0f)) best = index to gap
            floor = minOf(floor, sorted[index].bottom)
        }
        val split = best?.first ?: return null
        return sorted.subList(0, split) to sorted.subList(split, sorted.size)
    }

    /** Runs that no cut divides, gathered into lines: a run joins the line it overlaps by half its height. */
    private fun rowsOf(region: List<Run>): List<Row> {
        val lines = mutableListOf<MutableList<Run>>()
        for (run in region.sortedByDescending { it.top }) {
            val line = lines.lastOrNull()
            val lineBottom = line?.minOf { it.bottom }
            val overlap = if (lineBottom == null) 0f else run.top - maxOf(run.bottom, lineBottom)
            if (line != null && overlap >= run.height / 2) line += run else lines += mutableListOf(run)
        }
        return lines.map { line -> line.sortedBy { it.left }.let { Row(it, isTabular(it)) } }
    }

    /** A row with a gap inside it wider than words are spaced: a table's, not prose. */
    private fun isTabular(runs: List<Run>): Boolean {
        for (index in 1 until runs.size) {
            if (runs[index].left - runs[index - 1].right > CELL_GAP * em) return true
        }
        return false
    }
}

/** What goes between two rows: nothing after a removed hyphen, a line break, or a paragraph break. */
private fun PageText.separatorBefore(previous: Row, row: Row, newColumn: Boolean, em: Float): String {
    val last = previous.runs.last()
    if (text[last.end - 1] == HYPHEN_MARKER) return ""
    val gap = previous.bottom - row.top
    val table = previous.tabular || row.tabular || hasLeaders(previous) || hasLeaders(row)
    return if (newColumn || table || gap > PARAGRAPH_GAP * em) "\n\n" else "\n"
}

private fun PageText.hasLeaders(row: Row): Boolean = row.runs.any { LEADERS.containsMatchIn(text, it.start, it.end) }

private fun Regex.containsMatchIn(text: String, start: Int, end: Int): Boolean =
    find(text.subSequence(start, end)) != null

/** Dot leaders, as set or as an OCR reads them back: four or more dots, spaced or not. */
private val LEADERS = Regex("""(?:\.\s?){4,}""")

/** A gutter between text columns is at least this many line heights wide. */
private const val GUTTER = 1f

/** Each side of a gutter must hold this share of the region's width to be a text column. */
private const val MIN_COLUMN_SHARE = 0.25f

/** …and this many runs, so that a figure's stray label does not split a page. */
private const val MIN_COLUMN_RUNS = 3

/** A gap between two runs of one row wider than this many line heights separates table cells, not words. */
private const val CELL_GAP = 1.5f

/** A gap between rows wider than this many line heights is more than leading: the paragraph has ended. */
private const val PARAGRAPH_GAP = 0.8f
