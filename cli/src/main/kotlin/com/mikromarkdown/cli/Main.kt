package com.mikromarkdown.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.StreamInfo

class MikroMarkdownCommand : CliktCommand(name = "mikromarkdown") {
    private val file by argument("FILE", help = "Input file (reads stdin if omitted)").path(mustExist = true).optional()
    private val output by option("-o", "--output", help = "Output file (default: stdout)").path()
    private val extension by option("-x", "--extension", help = "File extension hint (e.g. html)")
    private val mimeType by option("-m", "--mime-type", help = "MIME type hint (e.g. text/html)")

    override fun run() {
        val mikroMarkdown = MikroMarkdown()

        val result = if (file != null) {
            mikroMarkdown.convert(file!!.toFile().absolutePath)
        } else {
            val info = StreamInfo(extension = extension, mimetype = mimeType)
            mikroMarkdown.convert(System.`in`.readBytes(), info)
        }

        if (output != null) {
            output!!.toFile().writeText(result.markdown)
        } else {
            print(result.markdown)
        }
    }
}

fun main(args: Array<String>) {
    // PDFBox pulls in the Log4j API; without a provider it writes a banner to stdout,
    // which would corrupt the Markdown we print there.
    System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
    System.setProperty("log4j2.simplelogLevel", "OFF")
    System.setProperty("log4j2.statusLoggerLevel", "OFF")
    MikroMarkdownCommand().main(args)
}
