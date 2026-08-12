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
fixed once for all formats. JVM and Android share one `jvmShared` source set, so a converter exists
once rather than per target; only PDF extraction and MIME detection are platform-specific. The model is public: `mid.parse(path)` returns the `Document`, and
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
between source sets (the drift that the `jvmShared` set removed).

ktfmt-gradle only derives tasks for the common and JVM source sets, so `library/build.gradle.kts`
registers matching tasks for the Android ones.

## Performance

Conversion itself is a few milliseconds; a CLI run is mostly JVM startup and class loading.
`:benchmark` measures the pipeline in-process, `scripts/benchmark.py` measures whole processes.

```bash
./gradlew :benchmark:run          # in-process, per stage
python3 scripts/benchmark.py      # whole process, against markitdown and anydoc
```

In-process, best of 50 runs after warmup:

| fixture | size | parse | render | total |
|---|---|---|---|---|
| test.json | 0.4 KB | 0.03 ms | 0.01 ms | 0.04 ms |
| test.epub | 2 KB | 0.42 ms | 0.00 ms | 0.42 ms |
| test_blog.html | 25 KB | 0.85 ms | 0.11 ms | 0.96 ms |
| test.xlsx | 11 KB | 1.09 ms | 0.00 ms | 1.03 ms |
| test.docx | 132 KB | 3.28 ms | 0.00 ms | 2.81 ms |
| test.pdf | 90 KB | 3.69 ms | 0.00 ms | 3.47 ms |
| test.pptx | 271 KB | 4.79 ms | 0.00 ms | 4.53 ms |
| test_wikipedia.html | 385 KB | 12.98 ms | 1.82 ms | 14.80 ms |

End to end the CLI runs in 100–240 ms, against ~25 ms for the Rust
[anydoc](https://github.com/firecrawl/anydoc) and 410–540 ms for Python markitdown. Nearly all of
what is left is process startup: `java -version` alone costs 41 ms on the same machine, and the
conversion is under 5 ms for every fixture but Wikipedia. Matching a native binary would take
ahead-of-time compilation, not a faster pipeline.

The CLI therefore optimizes startup rather than throughput:

- `installDist` records a [class-data-sharing](https://docs.oracle.com/en/java/javase/21/vm/class-data-sharing.html)
  archive into the distribution, which roughly halves startup. Set `MIKROMARKDOWN_NO_CDS=1` to skip
  it; the start script also skips it when the archive is missing, so `distZip` still works.
- it compiles with C1 only (`-XX:TieredStopAtLevel=1`), since C2 never pays for itself in a run this
  short. Embedders using the library get the normal JIT.

### Kotlin/Native spike

`:cli-native` builds a macOS binary carrying the converters that need no JVM library — CSV, JSON,
XML, plain text and Markdown passthrough. It shares the model, renderer and pipeline with every
other target, and its output is byte-identical to the JVM CLI's.

```bash
./gradlew :cli-native:linkReleaseExecutableMacosArm64
```

Whole-process, best of six, converting CSV of increasing size:

| input | native | anydoc (Rust) | JVM CLI |
|---|---|---|---|
| 1 KB | **3 ms** | 21 ms | 61 ms |
| 55 KB | **10 ms** | 25 ms | 73 ms |
| 172 KB | **25 ms** | 30 ms | 82 ms |
| 580 KB | 75 ms | **47 ms** | 114 ms |
| 1.8 MB | 237 ms | **93 ms** | 193 ms |

Startup is where a native binary wins and it wins outright: 3 ms against anydoc's 21 ms. Throughput
is where it loses — Kotlin/Native's allocation and GC costs run about 2x the JVM's on the per-cell
work of a large table, so anydoc leads from ~250 KB and the JVM CLI overtakes it around 1.5 MB.
Most documents are far below that crossover.

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
