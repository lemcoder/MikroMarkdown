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
| 5 | HTML inline runs skip the copy in `trimEdges` when there is nothing to trim | changed EPUB and both HTML outputs, twice, even with a stricter guard — the function does more than its name says | **reverted** |

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
