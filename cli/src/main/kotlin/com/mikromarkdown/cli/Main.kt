package com.mikromarkdown.cli

import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) {
    // PDFBox pulls in the Log4j API; without a provider it writes a banner to stdout,
    // which would corrupt the Markdown we print there.
    System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory")
    System.setProperty("log4j2.simplelogLevel", "OFF")
    System.setProperty("log4j2.statusLoggerLevel", "OFF")
    MikroMarkdownCommand().main(args)
}
