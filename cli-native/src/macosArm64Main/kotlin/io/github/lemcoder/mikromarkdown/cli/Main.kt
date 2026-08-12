package io.github.lemcoder.mikromarkdown.cli

import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.MikroMarkdownException
import kotlin.system.exitProcess

/**
 * Minimal native entry point, kept deliberately bare so its timings measure conversion rather than an argument parser.
 * The JVM CLI remains the full one.
 */
public fun main(args: Array<String>) {
    val path = args.firstOrNull()
    if (path == null) {
        println("usage: mikromarkdown <file>")
        exitProcess(2)
    }

    try {
        print(MikroMarkdown().convert(path).markdown)
    } catch (e: MikroMarkdownException) {
        // Unsupported formats must fail loudly: benchmarks and scripts read the exit code.
        println("error: ${e.message}")
        exitProcess(1)
    }
}
