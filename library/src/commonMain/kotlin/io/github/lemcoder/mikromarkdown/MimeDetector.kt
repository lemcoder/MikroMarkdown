package io.github.lemcoder.mikromarkdown

public fun interface MimeDetector {
    public fun detect(path: String): StreamInfo
}
