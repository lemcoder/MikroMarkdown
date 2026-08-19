# MikroMarkdown

[![Test](https://github.com/lemcoder/MikroMarkdown/actions/workflows/gradle.yml/badge.svg)](https://github.com/lemcoder/MikroMarkdown/actions/workflows/gradle.yml)

Kotlin Multiplatform library that converts documents to Markdown, on the JVM, Android and Kotlin/Native. Port of
Microsoft's [MarkItDown](https://github.com/microsoft/markitdown).

## Supported formats

Office formats were removed deliberately: DOCX, XLSX and PPTX are editing formats, while a reader
meets PDF and EPUB. Dropping them took Apache POI with them — the distribution went from 66 MB to
36 MB. If they are wanted back, they return as an opt-in module the way PDF is heading, rather than
as a dependency everyone carries.


| Format | Extension | Notes |
|--------|-----------|-------|
| EPUB | `.epub` | |
| HTML | `.html`, `.htm` | |
| PDF | `.pdf` | opt-in: `:pdfium` module, `register(PdfiumConverter())` |
| CSV | `.csv` | |
| JSON | `.json` | |
| XML | `.xml` | |
| Plain text | `.txt` and others | |
| Markdown | `.md` (passthrough) | |

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
fixed once for all formats. Every converter lives in `commonMain` and runs on every target, so the
library has one registration list rather than one per platform and no third-party JVM dependency at
all; only PDF is platform-specific, and it lives in its own module because it needs a native
library. The model is
public: `mid.parse(path)` returns the `Document`, and `ConversionResult.document` exposes it
alongside the rendered Markdown.

```kotlin
val document = mid.parse("/path/to/book.epub")
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
val result = mid.convert("/path/to/book.epub")

// from bytes with explicit format hint
val bytes = File("document.html").readBytes()
val result = mid.convert(bytes, StreamInfo(extension = "html"))

println(result.markdown)
println(result.title) // nullable, extracted from document metadata
```

### Android

Identical — `MikroMarkdown()` is one common function, and every converter it registers is common
code.

```kotlin
import io.github.lemcoder.mikromarkdown.MikroMarkdown

val mid = MikroMarkdown()

val result = mid.convert(file.absolutePath)
```

### PDF

PDF needs pdfium, so it ships as `:pdfium` and the caller opts in:

```kotlin
val mid = MikroMarkdown().apply { register(PdfiumConverter()) }
```

## Custom converters

Implement `DocumentConverter` and register it:

```kotlin
class MyConverter : DocumentConverter {
    override fun accepts(bytes: ByteArray, info: StreamInfo): Boolean =
        info.extension == "xyz"

    override fun parse(bytes: ByteArray, info: StreamInfo): Document =
        Document(blocks = listOf(Heading(1, "Custom"), Paragraph(bytes.decodeToString())))
}

val mid = MikroMarkdown()
mid.register(MyConverter())                  // default priority 0.0
mid.register(FallbackConverter(), priority = 10.0) // higher = later
```

Lower priority runs first. `PlainTextConverter` uses `10.0` so it acts as a fallback.

## Custom MIME detection

`MimeDetector` is a `fun interface` — pass a lambda or implement it. The default,
`SignatureMimeDetector`, reads the leading bytes and falls back to the extension; content sniffing
for extension-less text formats is where a full MIME registry such as Apache Tika goes, as your
dependency rather than the library's:

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
| [Konsist](https://docs.konsist.lemonappdev.com) | `./gradlew :library:jvmTest --tests '*ArchitectureTest*'` | pipeline boundaries and encapsulation |

`./gradlew check` runs all three. The library also builds in Kotlin's
[explicit API mode](https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors),
so every exported declaration states its visibility and return type.

The Konsist rules in `ArchitectureTest` encode the architecture, and each one is verified to fail
against a deliberate violation:

*Layering* — the model depends on nothing and stays free of `java.*`/`android.*`; converters never
import the renderer or each other; Markdown syntax appears only under `render/`.

*Encapsulation* — helpers under `utils` are never public, the model exposes no mutable state, and
every `DocumentConverter` is named `*Converter` and lives in `converters`.

*Hygiene* — no wildcard imports, no printing from library code, and no source file duplicated
between source sets — a rule that now has no exceptions, since every production file is common.

ktfmt-gradle only derives tasks for the common and JVM source sets, so `library/build.gradle.kts`
registers matching tasks for the Android ones.

## Performance

The command line tool is the Kotlin/Native binary; there is no JVM CLI. `:benchmark` measures the
library in-process on the JVM, and `scripts/optbench.py` A/B times the native binary against a saved
champion so that session-to-session drift cannot be mistaken for a change.

```bash
./gradlew :cli-native:linkReleaseExecutableMacosArm64
./gradlew :benchmark:run            # library, in-process, per stage
python3 scripts/optbench.py "..."   # native binary, against the champion
python3 scripts/benchmark.py        # whole process, against markitdown and anydoc
```

Whole process, best of ten, against the Rust [anydoc](https://github.com/firecrawl/anydoc):

| input | Kotlin/Native | anydoc (Rust) |
|---|---|---|
| 1 KB CSV | 3 ms | 3 ms |
| 172 KB CSV | 7 ms | 10 ms |
| 1.8 MB CSV | 51 ms | 71 ms |
| 3.5 MB CSV | 102 ms | 139 ms |
| EPUB | 5 ms | 3 ms |
| Wikipedia HTML | 65 ms | — |

Note when reproducing this: anydoc's npm package is a Node script loading a napi module, so timing
`node_modules/.bin/anydoc` charges Node's 15 ms startup to Rust and reads as ~22 ms flat. The figures
here come from its Rust binary, built from the vendored source with
`cargo build --release --example convert`.

`docs/optimization-log.md` records twenty-five measured experiments, nine of which survived, and the
two methodology mistakes that cost more than most of the wins.

## Benchmark

`scripts/benchmark.py` converts the test fixtures with the native binary, Python
[markitdown](https://github.com/microsoft/markitdown) and Rust
[anydoc](https://github.com/firecrawl/anydoc), then reports content recall, structure counts, table
integrity and timings to `build/benchmark/report.md`:

```bash
./gradlew :cli-native:linkReleaseExecutableMacosArm64
python3 scripts/benchmark.py
```

Engines whose CLI is missing are skipped. anydoc only handles binary formats, so it sits out the
HTML/JSON/XML fixtures.
