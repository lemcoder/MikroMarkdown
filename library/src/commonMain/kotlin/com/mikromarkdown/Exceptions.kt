package com.mikromarkdown

sealed class MarkItDownException(message: String, cause: Throwable? = null) : Exception(message, cause)

class UnsupportedFormatException(message: String) : MarkItDownException(message)

class FileConversionException(message: String, cause: Throwable? = null) : MarkItDownException(message, cause)
