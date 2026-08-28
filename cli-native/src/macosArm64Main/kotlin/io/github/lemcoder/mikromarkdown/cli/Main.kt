package io.github.lemcoder.mikromarkdown.cli

import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.MikroMarkdownException
import io.github.lemcoder.mikromarkdown.pdf.PdfiumConverter
import kotlin.system.exitProcess

/**
 * The command line tool, kept deliberately bare so its timings measure conversion rather than an argument parser.
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

    // PDF lives in its own module; the CLI opts in, the library does not depend on it.
    val mikroMarkdown = MikroMarkdown().apply { register(PdfiumConverter()) }

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
