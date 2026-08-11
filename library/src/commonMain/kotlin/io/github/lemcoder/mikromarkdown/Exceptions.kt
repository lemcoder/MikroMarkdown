package io.github.lemcoder.mikromarkdown

sealed class MikroMarkdownException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedFormatException(message: String) : MikroMarkdownException(message)

class FileConversionException(message: String, cause: Throwable? = null) : MikroMarkdownException(message, cause)
