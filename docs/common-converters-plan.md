# Plan: the remaining converters in commonMain

Status: proposal. Branch `common-converters`. Nothing implemented yet.

## Where things stand

Five converters and one helper are still JVM-only, plus PDF on each platform:

| file | lines | depends on |
|---|---|---|
| `HtmlToDocument.kt` | 334 | Jsoup |
| `PptxConverter.kt` | 237 | POI `XSLF*`, XMLBeans `CT*` |
| `DocxConverter.kt` | 164 | POI `XWPF*` |
| `EpubConverter.kt` | 132 | `java.util.zip`, `javax.xml`, Jsoup |
| `XlsxConverter.kt` | 64 | POI `XSSFWorkbook`, `DataFormatter` |
| `HtmlConverter.kt` | 15 | Jsoup, via the helper |
| `PdfConverter.kt` ×2 | 32 | PDFBox / pdfbox-android |

Moving them is not a file move: `commonMain` cannot use POI, Jsoup, `java.util.zip` or `javax.xml`,
so each is a rewrite against the raw format. CSV, JSON and XML made this trip already and their
output stayed byte-identical, which is the bar here too.

## Why bother

Two payoffs, and they are worth separating because they carry different risk appetites.

**iOS.** The document formats are the reason a SwiftUI reader cannot exist today. This work is the
blocker, and it is worth doing even if nothing gets faster.

**A smaller, faster JVM build.** POI dominates the 66 MB distribution, and a DOCX takes 183 ms end
to end against 2.7 ms of actual conversion — most of the rest is loading POI. Dropping commons-csv,
Jackson and kotlinx-serialization already cut a JSON conversion from 2063 classes to 1214; POI is a
much larger slice.

Against that: POI, Jsoup and PDFBox absorb an enormous amount of real-world malformation. Hand-written
parsers will be less forgiving, and the fixture corpus is ten files. **Expanding that corpus is part
of the work, not an afterthought.**

## Building blocks needed first

| need | choice | why |
|---|---|---|
| ZIP + inflate | `com.soywiz:korlibs-compression:6.0.0` | OOXML and EPUB are ZIP containers; no inflate in kotlinx-io or okio on native |
| XML parsing | `io.github.pdvrieze.xmlutil:core:0.91.1` | KMP pull parser; hand-rolling one is possible but entity and namespace handling is where such things go wrong |
| HTML parsing | `com.fleeksoft.ksoup:ksoup:0.2.6` | KMP port of Jsoup with a near-identical API, `macosarm64` published |

All three are dependencies we would carry in `commonMain`, replacing heavier JVM-only ones. Worth
confirming each builds for `macosArm64` in a throwaway module before committing to the sequence.

## Sequence

Ordered so each phase produces something shippable and the risky ones come last.

### Phase 0 — infrastructure

Add the three dependencies to `commonMain`, confirm the native target still links, and extend the
verification harness so `scripts/optbench.py` checks native output for every fixture a phase makes
available, not just CSV/JSON/XML. Nothing user-visible.

### Phase 1 — HTML, via Ksoup

`HtmlToDocument` is written against a Jsoup-shaped API, so the port is mostly mechanical: `Jsoup.parse`,
`childNodes()`, `attr`, `selectFirst`. The risk is not the API, it is the parser: Wikipedia is messy
HTML and Ksoup may recover from it differently. Acceptance is byte-identical output on `test_blog.html`
and `test_wikipedia.html`, and if it is not, the diff decides whether the difference is defensible.

Unlocks: HTML on native, and the EPUB rewrite.

### Phase 2 — EPUB

Needs ZIP (Phase 0) plus the XML parser for `container.xml` and the OPF, then reuses Phase 1 for the
chapters. The smallest of the container formats and a good first exercise of the ZIP reader.

### Phase 3 — DOCX

`word/document.xml`: `w:p`, `w:r`, `w:t`, `w:rPr` for bold/italic/strike, `w:pStyle` for heading
level, `w:numPr` for list level. Images resolve through `word/_rels/document.xml.rels` into
`word/media/`. We use three POI types today and a narrow slice of each, so the rewrite is bounded.

### Phase 4 — XLSX

`xl/workbook.xml` for sheet names, `xl/worksheets/sheetN.xml` for cells, `xl/sharedStrings.xml` for
text. **The catch is `DataFormatter`**: POI's implementation of Excel number formats runs to
thousands of lines, and we call it for every non-integer cell. Replicating it in general is out of
scope; the plan is to implement the common format codes and treat anything else as the raw value,
then check what the fixtures and a wider corpus actually exercise. This phase is the most likely to
change output, and the point where "byte-identical" may have to give way to "defensibly different".

### Phase 5 — PPTX

The largest: slides, shapes, group shapes, placeholders, pictures, tables, and charts across eight
chart types currently read through XMLBeans `CT*` classes. Chart XML is verbose but regular. Leave
it last because it is the most code for the least reach.

### Phase 6 — PDF

**Not a `commonMain` candidate.** There is no KMP PDF library, and text extraction with layout
analysis is a project in itself — anydoc wrote their own. Two options, to decide when the rest lands:

1. keep `expect`/`actual` with PDFBox on JVM and Android, and no PDF on other targets;
2. cinterop to pdfium or mupdf for native targets.

Option 1 is the honest default. Option 2 only pays if iOS PDF support is required.

## Ground rules per phase

1. The new implementation lands in `commonMain`; the `jvmShared` version is deleted in the same
   commit. The Konsist duplicate-file rule enforces that nothing is left copied per target.
2. `scripts/optbench.py` must report every fixture byte-identical on both targets before timings are
   read. Where a difference is deliberate, the fixture baseline is updated in the same commit, with
   the diff quoted in the message.
3. `PythonComparisonTest` stays at 100% token recall against Python markitdown.
4. Timings are A/B against the champion binary, never absolute — the log records why.
5. Each phase adds fixtures that exercise what it implements: a DOCX with numbered and nested lists,
   an XLSX with dates and currency, a PPTX with a chart. Ten fixtures is too few to rewrite parsers
   against.

## Estimate

| phase | effort | risk |
|---|---|---|
| 0 infrastructure | half a day | low — or the whole plan changes if a library will not build |
| 1 HTML | 1 day | medium — parser differences on messy input |
| 2 EPUB | half a day | low |
| 3 DOCX | 2-3 days | medium |
| 4 XLSX | 2-4 days | **high** — number formatting |
| 5 PPTX | 3-5 days | medium, mostly volume |
| 6 PDF | — | decide later |

Call it two weeks to get everything but PDF into `commonMain`, with XLSX the phase most likely to
need a scope conversation.

## Worth deciding before starting

- **Is byte-identical output a hard requirement, or a default that XLSX may negotiate?** The answer
  changes how Phase 4 is approached.
- **Should the JVM keep POI as an option?** A `mikromarkdown-poi` artifact could keep POI-backed
  converters for callers who value its tolerance of broken files over startup time. It costs an
  artifact and a registration path, and it would make the switch reversible per format.
- **Which targets beyond macOS?** Adding `iosArm64` and `linuxX64` early keeps the code honest;
  adding them late risks discovering a dependency that does not publish for them.
