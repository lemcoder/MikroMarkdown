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

End to end the CLI runs in 100–240 ms, against 3–6 ms for the Rust
[anydoc](https://github.com/firecrawl/anydoc) and 410–540 ms for Python markitdown. Nearly all of
what is left is process startup: `java -version` alone costs 41 ms on the same machine, and the
conversion is under 5 ms for every fixture but Wikipedia. Matching a native binary would take
ahead-of-time compilation, not a faster pipeline.

Note when reproducing this: anydoc's npm package is a Node script loading a napi module, so timing
`node_modules/.bin/anydoc` charges Node's 15 ms startup to Rust and reads as ~22 ms flat. The
figures here come from its Rust binary, built from the vendored source with
`cargo build --release --example convert`.

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

Whole-process, best of eight, converting CSV of increasing size:

| input | Kotlin/Native | anydoc (Rust binary) | anydoc (npm, via Node) | JVM CLI |
|---|---|---|---|---|
| 1 KB | 3 ms | 3 ms | 21 ms | 63 ms |
| 55 KB | 5 ms | 5 ms | 24 ms | 65 ms |
| 172 KB | 10 ms | 10 ms | 30 ms | 73 ms |
| 580 KB | 29 ms | 27 ms | 45 ms | 95 ms |
| 1.8 MB | 86 ms | 73 ms | 93 ms | 151 ms |
| 3.5 MB | 171 ms | 141 ms | 163 ms | 222 ms |

Kotlin/Native matches the Rust binary exactly up to about 172 KB — both are process startup at that
point — and trails it by 7% at 580 KB, growing to 21% at 3.5 MB. Against the JVM CLI it is 12x
faster on small inputs and still ahead at 3.5 MB.

Three changes closed the throughput gap that the first cut of this target showed:

- **The renderer stopped allocating when it has nothing to change.** Escaping now scans for the
  first character that needs a backslash and returns the input untouched when there is none, and a
  single-line table cell skips the split-and-rejoin. Ordinary cells — a word, a number — now cost no
  allocation at all. This is shared code, so the JVM got faster too.
- **The native CSV reader slices instead of accumulating.** Fields are ranges in the decoded text,
  so a field costs one substring rather than a per-character builder plus a separate trim. Only
  fields containing escaped quotes, which cannot be a slice of the input, assemble a string.
- **CSV, JSON and XML moved to `commonMain`**, taking commons-csv, Jackson and
  kotlinx-serialization with them. One implementation now serves every target: a slicing CSV reader,
  a JSON re-indenter that copies tokens verbatim so `1.50` does not become `1.5`, and an XML
  formatter. A JSON conversion loads 1214 classes instead of 2063, the native binary is 1.3 MB
  instead of 2.2 MB, and the JVM CLI converts JSON in 56 ms instead of 95 ms. Output is unchanged
  on every fixture.
- **Two quadratics in the renderer are gone.** Blocks are written into one buffer carrying a line
  prefix, rather than each block returning a string that its parent splits into lines and re-joins —
  which charged the deepest content once per level of nesting above it. And the entity check now
  scans ten characters ahead instead of searching the rest of the document for a semicolon.
  Measured on inputs built to provoke them, with output byte-identical before and after:

  | pathological input | before | after |
  |---|---|---|
  | 400-deep nested lists | 33.03 ms | **0.12 ms** |
  | 400 ampersands per cell, 789 KB | 9.39 ms | **2.42 ms** |

Together those took a 1.8 MB CSV from 237 ms to 87 ms. Kotlin/Native's remaining cost is the
document model itself: every cell becomes a `TableCell` holding a `Text` holding a `String`, which
is why peak memory is 125 MB for a 1.8 MB input. A genuinely zero-copy model — inlines holding
slices of the source buffer rather than copies — is the next lever, and a deeper change.

Compiler flags were measured rather than guessed. `-Xbinary=preCodegenInlineThreshold=40` is worth
about 8% on large inputs and ships. Every garbage collection setting tried was worse than the
default, and the collector is the interesting part of the story, so the numbers are below.

Converting 20 documents of 580 KB in one process:

| policy | time | peak RSS |
|---|---|---|
| default (adaptive) | 543 ms | 59 MB |
| `gcSchedulerType=manual`, never collecting | 428 ms | 954 MB |
| `gcSchedulerType=manual`, collecting between documents | 485 ms | 67 MB |
| `autotune = false` with a heap ceiling | 1246 ms | 43 MB |

A manual collector is genuinely faster, since a process that exits never needs to collect, and a
document boundary is the one place where everything the previous conversion allocated is provably
dead. But it only bounds growth *between* documents: a single large input still has nothing
collecting mid-parse, so the 3.5 MB file takes 228 MB either way and a much larger one would grow
until it failed. Turning `autotune` off is far worse than it looks like it should be — 8.6x on a
single 3.5 MB file — and the ceiling value makes no difference to that, so `targetHeapBytes` is not
behaving as its name suggests.

The default collector ships. The native CLI does accept several files per invocation, which is what
would make a manual policy workable if the trade ever becomes worth it.

### What did not help

Binary size does not drive startup, so shrinking it is not a performance lever:

| binary | size | startup |
|---|---|---|
| Kotlin/Native hello world | 485 KB | 3.2 ms |
| this CLI | 1.3 MB | 3.5 ms |
| anydoc (Rust) | 6 MB | 2.6 ms |

A 6 MB Rust binary starts faster than a 485 KB Kotlin/Native one, and stripping ours changed
nothing measurable. The 0.6 ms between the two runtimes is initialization, not size.

Replacing clikt with hand-rolled argument parsing removes 147 loaded classes and about 2 ms — inside
the noise, and not worth losing its help output and error handling. The dependency stays.

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
