# Plan: the remaining converters in commonMain

Status: proposal. Branch `common-converters`. Nothing implemented yet.

## Where things stand

**The office formats are gone.** DOCX, XLSX and PPTX were deleted rather than ported: they are
editing formats, and a reader meets PDF and EPUB. That took Apache POI with them — 66 MB of
distribution down to 36 MB, 35 jars to 23 — and removed the phase most likely to change output
silently. They can return as an opt-in module in the shape `:pdfium` is taking; the fixtures stay,
and a test pins that they currently raise `UnsupportedFormatException`.

What is left JVM-only is two converters, one helper, and PDF on each platform:

| file | lines | depends on | plan |
|---|---|---|---|
| `HtmlToDocument.kt` | 334 | Jsoup | → commonMain (Ksoup) |
| `EpubConverter.kt` | 132 | `java.util.zip`, `javax.xml`, Jsoup | → commonMain |
| `HtmlConverter.kt` | 15 | Jsoup, via the helper | → commonMain |
| `PdfConverter.kt` ×2 | 32 | PDFBox / pdfbox-android | → a separate `:pdfium` module |

Moving them is not a file move: `commonMain` cannot use POI, Jsoup, `java.util.zip` or `javax.xml`,
so each is a rewrite against the raw format. CSV, JSON and XML made this trip already and their
output stayed byte-identical, which is the bar for everything except PDF.

## Why bother

**iOS.** The document formats are the reason a SwiftUI reader cannot exist today. This is the blocker,
and it is worth doing even if nothing gets faster.

**A smaller JVM build** — already banked by the deletion, and worth being exact about what it bought:
36 MB instead of 66 MB, but **no faster**. POI was loaded lazily, so a CSV or EPUB conversion never
paid for it; the startup numbers did not move. Size and dependency surface improved, speed did not.

What remains to remove is Jsoup, and it goes when HTML moves to Ksoup.

Against all of it: POI, Jsoup and PDFBox absorb an enormous amount of real-world malformation.
Hand-written parsers will be less forgiving, and the fixture corpus is ten files. **Expanding it is
part of the work, not an afterthought.**

## Building blocks

| need | choice | notes |
|---|---|---|
| inflate | `com.soywiz:korlibs-compression:6.0.0` | EPUB is a ZIP; nothing in kotlinx-io or okio inflates on native. It ships **no ZIP reader**, so the central directory is ours to parse — about 150 lines |
| HTML parsing | `com.fleeksoft.ksoup:ksoup:0.2.6` | KMP port of Jsoup, near-identical API, `macosarm64` published |
| PDF | pdfium via cinterop + JNI | see below |

Verified on `macosArm64`, running rather than merely linking: Ksoup parses HTML and, in XML mode,
reads an OPF well enough to pull `dc:title`, manifest hrefs, spine idrefs and the cover meta —
**so no separate XML library is needed**. korlibs deflate round-trips (1000 bytes → 31 → 1000).
The korlibs API lives under `korlibs.io.compression.*`, not `korlibs.compression.*`.

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

Text needs `FPDF_InitLibrary`, `FPDF_LoadMemDocument`, `FPDF_GetPageCount`, `FPDF_LoadPage`,
`FPDFText_LoadPage`, `FPDFText_CountChars`, `FPDFText_GetText` (UTF-16), and the matching closes.
The existing `plainTextBlocks` reflow and de-hyphenation sit on top unchanged.

**Images matter as much as text here**, because the Compose and SwiftUI readers need them, so the
module walks page objects rather than only the text layer:

| step | call |
|---|---|
| iterate objects on a page | `FPDFPage_CountObjects`, `FPDFPage_GetObject` |
| keep the images | `FPDFPageObj_GetType` == `FPDF_PAGEOBJ_IMAGE` |
| where it sits on the page | `FPDFPageObj_GetBounds` |
| size and colour depth | `FPDFImageObj_GetImageMetadata` |
| how it is stored | `FPDFImageObj_GetImageFilterCount`, `FPDFImageObj_GetImageFilter` |
| the bytes | `FPDFImageObj_GetImageDataDecoded`, or `FPDFImageObj_GetRenderedBitmap` |

Two cases, and the second is the awkward one:

- **`DCTDecode` or `JPXDecode`** — the decoded stream *is* a JPEG or JPEG 2000 file, so the bytes go
  straight into an `Asset` with the matching media type. Free.
- **Anything else** (Flate-compressed raw pixels, which is very common) — there is no image file in
  the PDF, only pixels. `FPDFImageObj_GetRenderedBitmap` returns BGRA, and something has to encode
  it. `commonMain` has no PNG encoder, so we write one: PNG is a header, an IDAT of deflated
  scanlines and a CRC, and korlibs-compression is already there for the deflate. Perhaps 150 lines,
  and worth having anyway — no other target has an encoder either.

**Placement.** `FPDFPageObj_GetBounds` gives each image a rectangle and `FPDFText_GetCharBox` gives
the text the same, so images can be emitted in reading order by vertical position rather than dumped
at the end of the page. Without it a figure lands after the prose that discusses it. This is the same
problem the PDFBox route had and never solved.

**Scanned pages** — no text and one full-page image — are detectable (an image covering most of the
mediabox, almost no characters) and worth flagging rather than emitting as a wall of nothing.

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

### Phase 0 — infrastructure ✅
Ksoup and korlibs-compression are in `commonMain` and proven to run on the native binary. xmlutil
turned out to be unnecessary — Ksoup's XML mode covers EPUB's container and OPF. Still to do when a
phase needs it: extend `scripts/optbench.py` to verify native output for the fixtures it unlocks.

### Phase 1 — HTML, via Ksoup
`HtmlToDocument` is written against a Jsoup-shaped API, so the port is mechanical. The risk is the
parser, not the API: Wikipedia is messy and Ksoup may recover differently. Acceptance is
byte-identical `test_blog.html` and `test_wikipedia.html`; if it is not, the diff decides whether the
difference is defensible.

### Phase 2 — EPUB
ZIP plus the XML parser for `container.xml` and the OPF, then Phase 1 for the chapters. Smallest
container format, and a good first exercise of the ZIP reader.

### Phase 3 — PDF, the `:pdfium` module
Text first, then images and placement. Independent of the OOXML work, so it can run in parallel or
first if iOS PDF matters more.

### Phase 4 — FB2, if wanted
Book's Story parses FB2 with the same walker it uses for HTML, through Jsoup's XML mode. Ksoup has
that mode too, so once Phase 1 lands FB2 is a tag-mapping layer over `HtmlToDocument` — a format we
do not support today for about half a day's work. Worth it for a reader; skip it otherwise.

### Removed — DOCX, XLSX, PPTX
Deleted, not deferred. If they return it is as an `:office` module mirroring `:pdfium`: its own
Gradle module, its own dependency, registered by the caller rather than by the factory. The deleted
implementations are in the history, and the fixtures are still in the repository.

## Assets, across every phase

The model already carries them — `Asset(id, mediaType, bytes, name)`, `Document.assets` and
`Image.assetId` — but only DOCX populates them today. The readers need images from everything, so
each phase extracts what its format holds:

| format | where the images are | notes |
|---|---|---|
| docx | done | ids are the file name and can collide; needs fixing |
| pdf | page objects, as above | needs a PNG encoder for raw-pixel images |
| pptx | `XSLFPictureShape` today emits a fabricated `shapeName.jpg` and no bytes | real assets when the raw-XML rewrite lands |
| epub | `<img src>` resolved against the chapter directory into a zip entry we already hold | needs an asset resolver in the HTML walker |
| html | remote URLs stay URLs; `data:` URIs decode into assets | cheap |
| xlsx | deferred with the rest of XLSX | |

Three things this needs that do not exist yet:

1. **An asset policy.** DOCX inlines base64 today, which is why its output is 161 KB against
   markitdown's 4.6 KB. `MarkdownOptions` should carry `Inline` / `Reference` (write files, emit
   relative links) / `AltTextOnly`; the current `imagesAsText` and `maxInlineImageUrl` are a crude
   stand-in. A PDF full of figures makes this urgent rather than tidy.
2. **Stable asset ids.** The DOCX id is the embedded file name and repeats across parts.
3. **Intrinsic size on `Image`.** Width and height from the source, so a Compose or SwiftUI layout
   does not jump while loading. `FPDFImageObj_GetImageMetadata` provides it for PDF.

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
| 3 PDF via pdfium, text | 2 days | medium — binding is routine, packaging and output changes are not |
| 3b PDF images, PNG encoder, placement | 2-3 days | medium — the PNG writer is small but the reading-order interleave is fiddly |
| assets for epub, html, pptx | 1 day, spread across their phases | low |
| asset policy, ids, intrinsic size | half a day | low, but blocks the readers |
| 4 FB2, optional | half a day | low |

Roughly a week, images included, now that the office formats are out.

## Worth deciding before starting

- **Which targets beyond macOS?** Adding `iosArm64` and `linuxX64` early keeps the code honest;
  adding them late risks finding a dependency — or a pdfium archive — that does not fit.
- **How are pdfium binaries shipped to JVM users** — bundled per platform in the artifact, or
  downloaded at build time by the consumer? The first is convenient and large; the second is small
  and one more thing to go wrong.
