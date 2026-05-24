package com.mikromarkdown

import android.content.Context
import com.mikromarkdown.converters.PdfConverter
import com.mikromarkdown.utils.AndroidMimeDetector
import com.tom.roush.pdfbox.android.PDFBoxResourceLoader

fun MarkItDown(context: Context? = null): MarkItDown = MarkItDown(
    detectMime = AndroidMimeDetector::detect,
    extraConverters = if (context != null) {
        PDFBoxResourceLoader.init(context)
        listOf(PdfConverter() to 0.0)
    } else {
        emptyList()
    },
)
