package com.mikromarkdown.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.mikromarkdown.MarkItDown
import com.mikromarkdown.StreamInfo
import java.io.File

class MarkItDownCommand : CliktCommand(name = "markitdown") {
    private val file by argument("FILE", help = "Input file (reads stdin if omitted)").path(mustExist = true).optional()
    private val output by option("-o", "--output", help = "Output file (default: stdout)").path()
    private val extension by option("-x", "--extension", help = "File extension hint (e.g. html)")
    private val mimeType by option("-m", "--mime-type", help = "MIME type hint (e.g. text/html)")

    override fun run() {
        val markItDown = MarkItDown()

        val result = if (file != null) {
            markItDown.convert(file!!.toFile())
        } else {
            val info = StreamInfo(extension = extension, mimetype = mimeType)
            markItDown.convert(System.`in`, info)
        }

        if (output != null) {
            output!!.toFile().writeText(result.markdown)
        } else {
            print(result.markdown)
        }
    }
}

fun main(args: Array<String>) = MarkItDownCommand().main(args)
