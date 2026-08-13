package com.mikromarkdown.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.lemcoder.mikromarkdown.MikroMarkdown
import io.github.lemcoder.mikromarkdown.StreamInfo

class MikroMarkdownCommand : CliktCommand(name = "mikromarkdown") {
    private val files by
        argument("FILE", help = "Input files (reads stdin if omitted)").path(mustExist = true).multiple()
    private val output by option("-o", "--output", help = "Output file (default: stdout)").path()
    private val extension by option("-x", "--extension", help = "File extension hint (e.g. html)")
    private val mimeType by option("-m", "--mime-type", help = "MIME type hint (e.g. text/html)")

    override fun run() {
        val mikroMarkdown = MikroMarkdown()

        val markdown =
            if (files.isEmpty()) {
                val info = StreamInfo(extension = extension, mimetype = mimeType)
                mikroMarkdown.convert(System.`in`.readBytes(), info).markdown
            } else {
                files.joinToString("\n\n") { mikroMarkdown.convert(it.toFile().absolutePath).markdown }
            }

        if (output != null) output!!.toFile().writeText(markdown) else print(markdown)
    }
}
