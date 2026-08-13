package io.github.lemcoder.mikromarkdown

public fun interface MimeDetector {
    public fun detect(path: String): StreamInfo

    /**
     * Detects from content already in hand.
     *
     * The pipeline reads the file anyway, so a detector that only needs the leading bytes should not open it a second
     * time. Detectors that need the path keep the default.
     */
    public fun detect(path: String, bytes: ByteArray): StreamInfo = detect(path)
}
