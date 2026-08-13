package io.github.lemcoder.mikromarkdown.cli

import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.MikroMarkdownException
import kotlin.system.exitProcess

/**
 * Minimal native entry point, kept deliberately bare so its timings measure conversion rather than an argument parser.
 * The JVM CLI remains the full one.
 *
 * Several files may be given: a document boundary is the one point where everything the previous conversion allocated
 * is dead, which is what makes a manual collection policy possible at all. The default collector wins on measurement,
 * so none is applied — see the README.
 */
public fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: mikromarkdown <file>...")
        exitProcess(2)
    }

    val mikroMarkdown = MikroMarkdown()

    for (path in args) {
        try {
            print(mikroMarkdown.convert(path).markdown)
        } catch (e: MikroMarkdownException) {
            // Unsupported formats must fail loudly: benchmarks and scripts read the exit code.
            println("error: ${e.message}")
            exitProcess(1)
        }
    }
}
