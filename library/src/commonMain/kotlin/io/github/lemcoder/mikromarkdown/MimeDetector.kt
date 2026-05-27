package io.github.lemcoder.mikromarkdown

fun interface MimeDetector {
    fun detect(path: String): StreamInfo
}
