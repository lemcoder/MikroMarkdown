# Plan: the remaining converters in commonMain

Status: proposal. Branch `common-converters`. Nothing implemented yet.

## Where things stand

Five converters and one helper are still JVM-only, plus PDF on each platform:

| file | lines | depends on | plan |
|---|---|---|---|
| `HtmlToDocument.kt` | 334 | Jsoup | → commonMain (Ksoup) |
| `PptxConverter.kt` | 237 | POI `XSLF*`, XMLBeans `CT*` | → commonMain (raw XML) |
| `DocxConverter.kt` | 164 | POI `XWPF*` | → commonMain (raw XML) |
| `EpubConverter.kt` | 132 | `java.util.zip`, `javax.xml`, Jsoup | → commonMain |
| `HtmlConverter.kt` | 15 | Jsoup, via the helper | → commonMain |
| `XlsxConverter.kt` | 64 | POI `XSSFWorkbook`, `DataFormatter` | **stays on POI for now** |
| `PdfConverter.kt` ×2 | 32 | PDFBox / pdfbox-android | → a separate `:pdfium` module |

Moving them is not a file move: `commonMain` cannot use POI, Jsoup, `java.util.zip` or `javax.xml`,
so each is a rewrite against the raw format. CSV, JSON and XML made this trip already and their
output stayed byte-identical, which is the bar for everything except PDF.

## Why bother

**iOS.** The document formats are the reason a SwiftUI reader cannot exist today. This is the blocker,
and it is worth doing even if nothing gets faster.

**A smaller, faster JVM build** — but only partly, now. POI dominates the 66 MB distribution and most
of the 183 ms a DOCX takes end to end against 2.7 ms of actual conversion. Keeping XLSX on POI means
**POI stays on the JVM classpath**, so that payoff is deferred until XLSX moves too. The portability
payoff lands in full: native gets everything but XLSX.

Against all of it: POI, Jsoup and PDFBox absorb an enormous amount of real-world malformation.
Hand-written parsers will be less forgiving, and the fixture corpus is ten files. **Expanding it is
part of the work, not an afterthought.**

## Building blocks

| need | choice | notes |
|---|---|---|
| ZIP + inflate | `com.soywiz:korlibs-compression:6.0.0` | OOXML and EPUB are ZIP; no inflate in kotlinx-io or okio on native |
| XML parsing | `io.github.pdvrieze.xmlutil:core:0.91.1` | KMP pull parser; hand-rolling one is where entities and namespaces go wrong |
| HTML parsing | `com.fleeksoft.ksoup:ksoup:0.2.6` | KMP port of Jsoup, near-identical API, `macosarm64` published |
| PDF | pdfium via cinterop + JNI | see below |

Confirm each links for `macosArm64` in a throwaway module before committing to the sequence.

## PDF: a `:pdfium` module

PDFBox has no KMP equivalent and text extraction is its own project, so PDF goes native through
[pdfium](https://pdfium.googlesource.com/pdfium/), bound with
[KonanPlugin](https://github.com/lemcoder/KonanPlugin) (`io.github.lemcoder.konanplugin:1.2.0-alpha05`,
on the plugin portal). The plugin generates JNI bindings from the *same* `.def` file cinterop binds,
so one declaration serves Kotlin/Native, JVM and Android.

```
pdfium/
  src/nativeInterop/cinterop/pdfium.def   headers = fpdfview.h fpdf_text.h fpdf_doc.h
  src/commonMain/…/PdfiumConverter.kt     DocumentConverter over the shim
  src/commonMain/…/Pdfium.kt              expect: open, pageCount, pageText, close
  src/macosArm64Main/…/Pdfium.kt          actual over the cinterop klib
  src/jvmMain/…/Pdfium.kt                 actual over the generated JNI bridges
  build.gradle.kts                        konanplugin: cinterop + jvmInterops from one def
```

Binaries come from [`bblanchon/pdfium-binaries`](https://github.com/bblanchon/pdfium-binaries), which
publishes per-platform archives (`pdfium-mac-arm64.tgz`, `pdfium-linux-x64.tgz`,
`pdfium-android-arm64.tgz`, …) containing headers and a library. A Gradle task downloads and unpacks
a **pinned release with a checksum** into `build/pdfium/<target>/`; nothing binary is committed.

The API needed is small: `FPDF_InitLibrary`, `FPDF_LoadMemDocument`, `FPDF_GetPageCount`,
`FPDF_LoadPage`, `FPDFText_LoadPage`, `FPDFText_CountChars`, `FPDFText_GetText` (UTF-16),
and the matching closes. The existing `plainTextBlocks` reflow and de-hyphenation sit on top unchanged.

**The module does not register itself.** `:library` keeps no PDF dependency, and a caller opts in:

```kotlin
val mikroMarkdown = MikroMarkdown().apply { register(PdfiumConverter()) }
```

That keeps `:pdfium` genuinely extractable — delete the module and the rest still builds.

### What this costs

- **PDF output will change.** pdfium and PDFBox extract text differently, so the `test.pdf` baseline
  has to be re-recorded. Byte-identity cannot be the gate here; `PythonComparisonTest`'s token recall
  against Python markitdown becomes the check, plus a read of the diff.
- **Distribution gets heavier and platform-shaped.** The JVM artifact needs the pdfium library and the
  generated stub per platform, 4-8 MB each. Building stubs for every JVM host means a CI matrix —
  from one machine we can only produce the host's.
- **Licensing**: pdfium is BSD-3-Clause with Apache-2.0 parts; the notices ship with the artifact.

## Sequence

Each phase is shippable on its own, risky ones last.

### Phase 0 — infrastructure
Add the three commonMain dependencies, confirm the native target still links, and extend
`scripts/optbench.py` so it verifies native output for every fixture a phase unlocks.

### Phase 1 — HTML, via Ksoup
`HtmlToDocument` is written against a Jsoup-shaped API, so the port is mechanical. The risk is the
parser, not the API: Wikipedia is messy and Ksoup may recover differently. Acceptance is
byte-identical `test_blog.html` and `test_wikipedia.html`; if it is not, the diff decides whether the
difference is defensible.

### Phase 2 — EPUB
ZIP plus the XML parser for `container.xml` and the OPF, then Phase 1 for the chapters. Smallest
container format, and a good first exercise of the ZIP reader.

### Phase 3 — PDF, the `:pdfium` module
Independent of the OOXML work, so it can run in parallel or first if iOS PDF matters more.

### Phase 4 — DOCX
`word/document.xml`: `w:p`, `w:r`, `w:t`, `w:rPr` for bold/italic/strike, `w:pStyle` for heading level,
`w:numPr` for list level; images through `word/_rels/document.xml.rels` into `word/media/`. We use
three POI types and a narrow slice of each, so the rewrite is bounded.

### Phase 5 — PPTX
The largest: slides, shapes, group shapes, placeholders, pictures, tables, and charts across eight
chart types currently read through XMLBeans. Chart XML is verbose but regular. Last because it is the
most code for the least reach.

### Not now — XLSX
Stays on POI. `DataFormatter` implements Excel's number-format language in thousands of lines and we
call it for every non-integer cell; reimplementing it is a project of its own and the phase most
likely to change output silently. Revisit once the rest has landed and the corpus is wider — and note
that until then the JVM build still carries POI.

## Ground rules per phase

1. The new implementation lands in `commonMain`; the `jvmShared` version is deleted in the same
   commit. The Konsist duplicate-file rule keeps anything from being copied per target.
2. `scripts/optbench.py` must report every fixture byte-identical on both targets before timings are
   read. Where a difference is deliberate — PDF, and possibly HTML — the baseline is updated in the
   same commit with the diff quoted in the message.
3. `PythonComparisonTest` stays at 100% token recall.
4. Timings are A/B against the champion binary, never absolute.
5. Each phase adds fixtures for what it implements: a DOCX with numbered and nested lists, a PPTX with
   a chart, a PDF with columns and hyphenation. Ten fixtures is too few to rewrite parsers against.

## Estimate

| phase | effort | risk |
|---|---|---|
| 0 infrastructure | half a day | low — or the plan changes, if a library will not build |
| 1 HTML | 1 day | medium — parser differences on messy input |
| 2 EPUB | half a day | low |
| 3 PDF via pdfium | 2-3 days | medium — binding is routine, packaging and output changes are not |
| 4 DOCX | 2-3 days | medium |
| 5 PPTX | 3-5 days | medium, mostly volume |

Roughly a week and a half without XLSX.

## Worth deciding before starting

- **Which targets beyond macOS?** Adding `iosArm64` and `linuxX64` early keeps the code honest;
  adding them late risks finding a dependency — or a pdfium archive — that does not fit.
- **Does the JVM keep POI-backed converters as an option?** A `mikromarkdown-poi` artifact would let
  callers choose POI's tolerance of broken files over startup time, and would make each format's
  switch reversible. It costs an artifact and a registration path.
- **How are pdfium binaries shipped to JVM users** — bundled per platform in the artifact, or
  downloaded at build time by the consumer? The first is convenient and large; the second is small
  and one more thing to go wrong.
