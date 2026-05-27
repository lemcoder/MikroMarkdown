# MikroMarkdown

[![Test](https://github.com/lemcoder/MikroMarkdown/actions/workflows/gradle.yml/badge.svg)](https://github.com/lemcoder/MikroMarkdown/actions/workflows/gradle.yml)

Kotlin Multiplatform (JVM + Android) library that converts documents to Markdown. Port of Microsoft's [MarkItDown](https://github.com/microsoft/markitdown).

## Supported formats

| Format | Extension |
|--------|-----------|
| Word | `.docx` |
| Excel | `.xlsx` |
| PowerPoint | `.pptx` |
| EPUB | `.epub` |
| HTML | `.html`, `.htm` |
| PDF | `.pdf` |
| CSV | `.csv` |
| JSON | `.json` |
| XML | `.xml` |
| Plain text | `.txt` and others |
| Markdown | `.md` (passthrough) |

## Setup

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.lemcoder:mikromarkdown:0.1.0")
}
```

## Usage

### JVM

```kotlin
import io.github.lemcoder.mikromarkdown.MarkItDown

val mid = MarkItDown()

// from file path
val result = mid.convert("/path/to/document.docx")

// from bytes with explicit format hint
val bytes = File("document.html").readBytes()
val result = mid.convert(bytes, StreamInfo(extension = "html"))

println(result.markdown)
println(result.title) // nullable, extracted from document metadata
```

### Android

```kotlin
import io.github.lemcoder.mikromarkdown.MarkItDown

// pass Context to enable PDF support
val mid = MarkItDown(context)

val result = mid.convert(file.absolutePath)
```

## Custom converters

Implement `DocumentConverter` and register it:

```kotlin
class MyConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean =
        info.extension == "xyz"

    override fun convert(bytes: ByteArray, info: StreamInfo): ConversionResult =
        ConversionResult(markdown = String(bytes))
}

val mid = MarkItDown()
mid.register(MyConverter())                  // default priority 0.0
mid.register(FallbackConverter(), priority = 10.0) // higher = later
```

Lower priority runs first. `PlainTextConverter` uses `10.0` so it acts as a fallback.

## Custom MIME detection

`MimeDetector` is a `fun interface` — pass a lambda or implement it:

```kotlin
val mid = MikroMarkdown(MimeDetector { path ->
    StreamInfo(extension = path.substringAfterLast('.'))
})
mid.register(HtmlConverter())
```

## Exceptions

| Exception | When |
|-----------|------|
| `UnsupportedFormatException` | No registered converter accepted the input |
| `FileConversionException` | Converter threw during conversion |

Both extend `MarkItDownException`.
