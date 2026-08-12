package io.github.lemcoder.mikromarkdown

public sealed class MikroMarkdownException(message: String, cause: Throwable? = null) : Exception(message, cause)

public class UnsupportedFormatException(message: String) : MikroMarkdownException(message)

public class FileConversionException(message: String, cause: Throwable? = null) : MikroMarkdownException(message, cause)
