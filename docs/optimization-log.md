# Optimization log

Each entry is one idea, implemented and measured against the same harness, then kept or reverted.
Numbers are whole-process wall time, best of ten, in milliseconds.

Run an experiment with:

```bash
python3 scripts/optbench.py "what I changed"
```

The harness refuses to report timings unless every fixture still renders byte-identically on both
the JVM and the native CLI, so a "faster" result can never be a broken one.

## Method, and a correction

The first seven iterations compared absolute timings taken minutes apart. That was wrong: the same
unchanged binary measured 60 ms in one session and 74 ms in the next, so several "wins" were drift.
Re-measured properly, **iterations 1-4 together are worth -4%, not the -20% first recorded.**

The harness now keeps a champion binary and interleaves it with the candidate inside a single run,
reporting the delta between them. Only a change that beats the champion in that comparison is kept,
and it then becomes the champion.

## Results

| # | idea | result | verdict |
|---|---|---|---|
| 1 | table rows written straight into the document buffer instead of `joinToString` per row | see correction above; 1-4 together -4% | **kept** |
| 2 | tables with no merged cells and no padding skip the intermediate cell lists | part of the -4% above | **kept** |
| 3 | CSV scans raw UTF-8 bytes and decodes only field ranges, instead of decoding the whole file first | time flat, native peak RSS 125→81 MB | **kept** for the memory |
| 4 | 128-entry lookup table rejects characters that cannot start markup before the escape checks | part of the -4% above | **kept** |
| 6 | single-text paragraphs skip the string builder, as table cells already do | noise | **reverted** |
| 7 | `appendLines` copies whole lines rather than one character at a time | first measured as a 23% regression, which turned out to be session drift | **retested as 8** |
| 8 | same, measured A/B against the champion | native -4% at 1.8 MB, jvm -1% | **kept** |
| 9 | CSV builds cells directly instead of mapping a second list | +2% at 1.8 MB, nothing elsewhere | **reverted** |
| 10 | CDS archive trained on six formats, class lists merged from separate runs | wiki -13%, pdf -22%, but json +16% and docx +6%: merging loses the loader metadata | **reverted** |
| 11 | JVM CLI accepts several files, so one recording run covers every format with metadata intact | pdf 203→160, wiki 139→124, json and csv and docx unchanged | **kept** |
| 12 | output buffer pre-sized from the block structure | +6% at 1.8 MB — the estimate over-allocates and the big up-front buffer costs more than growing | **reverted** |
| 13 | HTML text nodes skip the whitespace regex when already normalized | neutral everywhere | **reverted** |
| 15 | CSV avoids the `drop(1)` copy of the record list | neutral | **reverted** |
| 16 | PDF skips building a word vocabulary when no line ends in a hyphen | changed PDF output: the scan misses `- \n`, and the vocabulary decides whether a hyphen survives | **reverted** |
| 17 | the file is read once and detection reuses those bytes, instead of opening it twice | -1% on all five measurements | **kept** |
| 18 | JSON formatter returns an index instead of allocating a pair per container | -1% on a 2.6 MB JSON, once a JSON big enough to measure was added | **kept** |
| 19 | escaping copies the runs between escapes in bulk rather than character by character | neutral: most text needs no escaping at all, so the loop rarely runs | **reverted** |
| 20 | raw blocks skip the newline rewrites when they would change nothing | neutral — raw blocks only carry plain text and Markdown passthrough | **reverted** |
| 21 | native concurrent mark-and-sweep collector | +9% at 1.8 MB | **reverted** |
| 22 | native `smallBinary` codegen | binary 1302→1193 KB but +6% at 1.8 MB | **reverted** |
| 23 | table rows taken from the table's own children instead of a subtree selector | neutral — Wikipedia's tables have 77 rows between them | **reverted** |
| 24 | a plain table cell keeps its string and builds `List<Inline>` only if asked | **-22% at 1.8 MB, -20% at 580 KB** | **kept** |
| 25 | CSV builds cells while scanning the bytes, now that a cell is cheap | -5% at 1.8 MB | **kept** |
| 14 | JVM flags: SerialGC, small heap, disabled verification | SerialGC and heap sizing both slower; the "20 ms" from disabled verification was the JVM refusing to start, caught only because output was checked afterwards | **reverted** |
| 5 | HTML inline runs skip the copy in `trimEdges` when there is nothing to trim | changed EPUB and both HTML outputs, twice, even with a stricter guard — the function does more than its name says | **reverted** |

## Outcome

Nine of twenty-five ideas survived. Against the binary this session started from:

| workload | before | after |
|---|---|---|
| 580 KB CSV | 28 ms | **20 ms** (-28%) |
| 1.8 MB CSV | 76 ms | **54 ms** (-30%) |
| Wikipedia, DOCX, PDF, JSON | — | unchanged |

Measured again on an idle machine for the final comparison, the native CLI runs the 580 KB file in
19 ms and the 1.8 MB one in 51 ms, against the Rust binary's 25 ms and 71 ms. It began this work
18-21% behind those.

Almost all of it came from one idea: a table cell that does not wrap its text in a list and a `Text`
until something asks for them (24), worth -22% on its own. The next largest was the CDS archive
learning more than one format (11), worth -21% on PDF. Everything else was a percent or two, and
sixteen ideas were worth nothing at all.

Two lessons cost more than the wins:

- **Absolute timings drift.** Comparing runs minutes apart credited iterations 1-4 with -20% when
  they were worth -4%. Every number here now comes from a champion and a candidate interleaved in
  one run.
- **A benchmark that does not check its output measures nothing.** Disabling bytecode verification
  looked like a 20 ms conversion; it was the JVM refusing to start. The harness verifies before it
  times, and the one experiment run outside it was the one that lied.

## Already settled before this log

Recorded so they are not retried:

- **smaller binary** — no effect on startup; a 6 MB Rust binary starts faster than a 485 KB
  Kotlin/Native one. Stripping ours changed nothing.
- **hand-rolled argument parsing instead of clikt** — 147 fewer classes, ~2 ms, inside noise.
- **zero-copy `CharSequence` slices into the source** — 35-40% *slower*: every character read
  becomes an interface call, and the renderer reads every character anyway.
- **`gcSchedulerType=manual`** — 21% faster in batch, 16x the memory. `autotune=false` with a heap
  ceiling: 8.6x slower.
- **GC binary options** (`stwms`, single-threaded mark, `aggressive`) — all neutral or worse.
- **`-Xbinary=preCodegenInlineThreshold=40`** — ~8% on large inputs, kept.
