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

## Architecture

Every format is parsed into one shared document model, and a single renderer serializes that model
to GitHub-Flavored Markdown:

```
bytes ──► MimeDetector ──► DocumentConverter.parse ──► Document ──► MarkdownRenderer ──► Markdown
                                (per format)          (blocks,        (one GFM
                                                       inlines,        serializer)
                                                       tables,
                                                       assets)
```

Converters contain no Markdown syntax, so escaping, table shaping, list indentation and spacing are
fixed once for all formats. The model is public: `mid.parse(path)` returns the `Document`, and
`ConversionResult.document` exposes it alongside the rendered Markdown.

```kotlin
val document = mid.parse("/path/to/report.docx")
document.blocks.filterIsInstance<Table>().forEach { println(it.rows.size) }

// Render with different options
val compact = MarkdownRenderer(MarkdownOptions(padTableColumns = true, imagesAsText = true))
println(compact.render(document))
```

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
import io.github.lemcoder.mikromarkdown.MikroMarkdown

val mid = MikroMarkdown()

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
import io.github.lemcoder.mikromarkdown.MikroMarkdown

// pass Context to enable PDF support
val mid = MikroMarkdown(context)

val result = mid.convert(file.absolutePath)
```

## Custom converters

Implement `DocumentConverter` and register it:

```kotlin
class MyConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean =
        info.extension == "xyz"

    override fun parse(bytes: ByteArray, info: StreamInfo): Document = document {
        heading(1, "Custom")
        paragraph(bytes.decodeToString())
    }
}

val mid = MikroMarkdown()
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

Both extend `MikroMarkdownException`.

## Code quality

| tool | task | what it guards |
|---|---|---|
| [ktfmt](https://github.com/facebook/ktfmt) | `./gradlew ktfmtFormat` / `ktfmtCheck` | formatting (kotlinlang style, 120 columns) |
| [detekt](https://detekt.dev) | `./gradlew detekt` | static analysis; overrides in `config/detekt/detekt.yml` |
| [Konsist](https://docs.konsist.lemonappdev.com) | `./gradlew :library:jvmTest --tests '*ArchitectureTest*'` | pipeline boundaries |

`./gradlew check` runs all three. The Konsist rules encode the architecture: the model depends on
nothing, converters never import the renderer, and Markdown syntax appears only under `render/` —
so a converter cannot quietly start building Markdown strings again.

ktfmt-gradle only derives tasks for the common and JVM source sets, so `library/build.gradle.kts`
registers matching tasks for the Android ones.

## Benchmark

`scripts/benchmark.py` converts the test fixtures with MikroMarkdown, Python
[markitdown](https://github.com/microsoft/markitdown) and Rust
[anydoc](https://github.com/firecrawl/anydoc), then reports content recall, structure counts, table
integrity and timings to `build/benchmark/report.md`:

```bash
./gradlew :cli:installDist
python3 scripts/benchmark.py
```

Engines whose CLI is missing are skipped. anydoc only handles binary formats, so it sits out the
HTML/JSON/XML fixtures.
