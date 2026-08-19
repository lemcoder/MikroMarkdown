package io.github.lemcoder.mikromarkdown.benchmark

import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.StreamInfo
import java.io.File
import kotlin.system.measureNanoTime

/**
 * In-process timings for the conversion pipeline.
 *
 * The CLI's wall clock is dominated by JVM startup and class loading, which says nothing about the pipeline itself.
 * This measures the stages separately on a warmed-up JVM, and separately reports the first conversion in a fresh JVM —
 * the one that pays for class loading.
 *
 * Usage: ./gradlew :benchmark:run --args="[fixtureDir] [warmup] [iterations]"
 */
fun main(args: Array<String>) {
    // Cold modes run exactly one conversion and exit, so the number includes class loading.
    // Comparing them isolates what format detection costs on a cold JVM.
    when (args.firstOrNull()) {
        "cold-bytes" -> return coldBytes(File(args[1]))
        "cold-path" -> return coldPath(File(args[1]))
    }

    val fixtures = File(args.getOrElse(0) { "library/src/commonTest/resources/test_files" })
    val warmup = args.getOrElse(1) { "20" }.toInt()
    val iterations = args.getOrElse(2) { "50" }.toInt()

    val files = fixtures.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.sortedBy { it.name }
    require(files.isNotEmpty()) { "no fixtures in ${fixtures.absolutePath}" }

    val first = files.first()
    val coldStart = measureNanoTime { MikroMarkdown().convert(first.absolutePath) }
    report("first conversion in a fresh JVM, class loading included (${first.name})", coldStart)
    println()

    val mikroMarkdown = MikroMarkdown()

    println("Best of $iterations runs after $warmup warmup runs, milliseconds.")
    println()
    println("| fixture | KB | parse | render | convert(bytes) | convert(path) |")
    println("|---|---|---|---|---|---|")

    for (file in files) {
        val bytes = file.readBytes()
        val info = StreamInfo(extension = file.extension, filename = file.name, localPath = file.absolutePath)

        repeat(warmup) { mikroMarkdown.convert(bytes, info) }

        val parse = best(iterations) { mikroMarkdown.parse(bytes, info) }
        val convertBytes = best(iterations) { mikroMarkdown.convert(bytes, info) }
        val convertPath = best(iterations) { mikroMarkdown.convert(file.absolutePath) }
        // Rendering is whatever convert adds on top of parse; timing it alone would re-parse.
        val render = (convertBytes - parse).coerceAtLeast(0)

        println(
            "| ${file.name} | ${file.length() / 1024} | ${parse.ms()} | ${render.ms()} | " +
                "${convertBytes.ms()} | ${convertPath.ms()} |"
        )
    }
}

private fun coldBytes(file: File) {
    val bytes = file.readBytes()
    val info = StreamInfo(extension = file.extension, filename = file.name, localPath = file.absolutePath)
    val elapsed = measureNanoTime { MikroMarkdown().convert(bytes, info) }
    report("cold convert(bytes), no detection (${file.name})", elapsed)
}

private fun coldPath(file: File) {
    val elapsed = measureNanoTime { MikroMarkdown().convert(file.absolutePath) }
    report("cold convert(path), with detection (${file.name})", elapsed)
}

private fun report(label: String, nanos: Long) = println("$label: ${nanos.ms()} ms")

private fun best(iterations: Int, block: () -> Any?): Long {
    var best = Long.MAX_VALUE
    repeat(iterations) {
        val elapsed = measureNanoTime { block() }
        if (elapsed < best) best = elapsed
    }
    return best
}

private fun Long.ms(): String = "%.2f".format(this / 1_000_000.0)
