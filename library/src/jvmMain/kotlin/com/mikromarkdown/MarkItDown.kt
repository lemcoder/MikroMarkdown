package com.mikromarkdown

import com.mikromarkdown.converters.PdfConverter
import com.mikromarkdown.utils.TikaMimeDetector

fun MarkItDown(): MarkItDown = MarkItDown(
    detectMime = TikaMimeDetector::detect,
    extraConverters = listOf(PdfConverter() to 0.0),
)
