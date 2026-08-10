#!/usr/bin/env python3
"""Compare MikroMarkdown's Markdown against markitdown (Python) and anydoc (Rust).

Usage:
    ./gradlew :cli:installDist
    python3 scripts/benchmark.py [--fixtures DIR] [--out DIR]

Engines are skipped (not failed) when their CLI is unavailable:
    mikromarkdown  cli/build/install/cli/bin/cli
    markitdown     `markitdown` on PATH, else `uvx markitdown[all]`
    anydoc         `anydoc` on PATH, else a local npm install of @firecrawl/anydoc

Metrics per output:
    content recall  tokens agreed on by >=2 engines that this engine also emits
    structure       headings / tables / list items / links / images / code fences
    table health    share of table rows whose column count matches the header
    hygiene         3+ blank-line runs, trailing whitespace, unescaped pipes
    speed           best of N CLI runs, and the same minus measured JVM startup
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TOKEN_SPLIT = re.compile(r"[\s!\"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~]+")
RUNS = 3


def tokens(text: str) -> set[str]:
    # Unicode spacing (nbsp, thin/hair spaces) differs per engine; normalize before comparing.
    text = re.sub("[\u00A0\u2000-\u200A\u202F\u205F\u3000]", " ", text)
    return {t for t in TOKEN_SPLIT.split(text.lower()) if len(t) > 3}


@dataclass
class Engine:
    name: str
    command: list[str] | None
    note: str = ""
    # Formats the engine refuses outright; skipped rather than counted as a failure.
    unsupported: set[str] = field(default_factory=set)

    def run(self, path: Path) -> tuple[str | None, float]:
        if self.command is None or path.suffix.lstrip(".").lower() in self.unsupported:
            return None, 0.0
        best = float("inf")
        out = None
        for _ in range(RUNS):
            start = time.perf_counter()
            proc = subprocess.run(
                self.command + [str(path)], capture_output=True, text=True, timeout=180
            )
            elapsed = time.perf_counter() - start
            if proc.returncode != 0:
                return None, 0.0
            out = proc.stdout
            best = min(best, elapsed)
        return out, best


def which_engines() -> list[Engine]:
    engines: list[Engine] = []

    cli = REPO / "cli/build/install/cli/bin/cli"
    engines.append(
        Engine("mikromarkdown", [str(cli)] if cli.exists() else None,
               "" if cli.exists() else "run ./gradlew :cli:installDist")
    )

    if shutil.which("markitdown"):
        markitdown = ["markitdown"]
    elif shutil.which("uvx"):
        markitdown = ["uvx", "--quiet", "markitdown[all]"]
    else:
        markitdown = None
    engines.append(Engine("markitdown", markitdown, "" if markitdown else "pip install markitdown[all]"))

    anydoc_bin = shutil.which("anydoc")
    if not anydoc_bin:
        for candidate in (
            REPO / "build/anydoc/node_modules/.bin/anydoc",
            Path("/tmp/anydoc-bench/node_modules/.bin/anydoc"),
        ):
            if candidate.exists():
                anydoc_bin = str(candidate)
                break
    engines.append(
        Engine(
            "anydoc",
            [anydoc_bin] if anydoc_bin else None,
            "" if anydoc_bin else "npm i @firecrawl/anydoc",
            # anydoc converts binary document formats only.
            unsupported={"html", "htm", "json", "xml", "md", "txt"},
        )
    )
    return engines


TABLE_ROW = re.compile(r"^\s*\|.*\|\s*$")
DELIMITER_ROW = re.compile(r"^\s*\|(\s*:?-{1,}:?\s*\|)+\s*$")


def table_stats(text: str) -> tuple[int, int, int]:
    """Returns (tables, rows, rows whose column count differs from the header)."""
    tables = rows = broken = 0
    columns = None
    in_table = False
    for line in text.splitlines():
        if TABLE_ROW.match(line):
            cells = len(line.strip().strip("|").split("|"))
            if not in_table:
                in_table = True
                tables += 1
                columns = cells
            elif DELIMITER_ROW.match(line):
                if cells != columns:
                    broken += 1
            else:
                rows += 1
                if cells != columns:
                    broken += 1
        else:
            in_table = False
            columns = None
    return tables, rows, broken


def hygiene(text: str) -> dict[str, int]:
    return {
        "blank_runs": len(re.findall(r"\n{4,}", text)),
        "trailing_ws": len(re.findall(r"(?m)[ \t]+$", text)),
        "empty_links": len(re.findall(r"\[\]\(", text)),
        "raw_html": len(re.findall(r"(?m)<(?!br>|!--|/)[a-zA-Z][a-zA-Z0-9]*[ >]", text)),
    }


def structure(text: str) -> dict[str, int]:
    tables, rows, broken = table_stats(text)
    return {
        "headings": len(re.findall(r"(?m)^#{1,6} \S", text)),
        "list_items": len(re.findall(r"(?m)^\s*(?:[-*+]|\d+\.) \S", text)),
        "links": len(re.findall(r"(?<!!)\[[^\]]*]\([^)]*\)", text)),
        "images": len(re.findall(r"!\[[^\]]*]\([^)]*\)", text)),
        "code_fences": len(re.findall(r"(?m)^```", text)) // 2,
        "tables": tables,
        "table_rows": rows,
        "broken_rows": broken,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixtures", default=str(REPO / "library/src/commonTest/resources/test_files"))
    parser.add_argument("--out", default=str(REPO / "build/benchmark"))
    args = parser.parse_args()

    fixtures = sorted(Path(args.fixtures).iterdir())
    fixtures = [f for f in fixtures if f.is_file() and not f.name.startswith(".")]
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    engines = which_engines()
    for engine in engines:
        state = "ready" if engine.command else f"MISSING ({engine.note})"
        print(f"{engine.name:14} {state}", file=sys.stderr)

    # JVM startup cost, so the Kotlin CLI's timing can be reported with and without it.
    jvm_baseline = 0.0
    mikro = next(e for e in engines if e.name == "mikromarkdown")
    if mikro.command:
        probe = out_dir / "_probe.txt"
        probe.write_text("x\n")
        _, jvm_baseline = mikro.run(probe)
        probe.unlink()

    results: dict[str, dict[str, object]] = {}
    for fixture in fixtures:
        print(f"converting {fixture.name}", file=sys.stderr)
        per_engine = {}
        for engine in engines:
            text, elapsed = engine.run(fixture)
            if text is None:
                continue
            (out_dir / f"{engine.name}_{fixture.name}.md").write_text(text)
            per_engine[engine.name] = {"text": text, "seconds": elapsed}
        results[fixture.name] = per_engine

    lines: list[str] = ["# Conversion benchmark", ""]
    lines.append(f"Fixtures: {len(fixtures)} · runs per file: {RUNS} (best kept) · "
                 f"measured JVM startup: {jvm_baseline * 1000:.0f} ms")
    lines.append("")
    lines.append("Recall is against the *consensus* vocabulary: tokens (>3 chars) that at least two "
                 "engines emit for the same file. Missing tokens mean dropped content.")
    lines.append("")

    totals: dict[str, Counter] = {e.name: Counter() for e in engines}
    recall_sum: dict[str, list[float]] = {e.name: [] for e in engines}

    for name, per_engine in results.items():
        if not per_engine:
            continue
        token_sets = {k: tokens(v["text"]) for k, v in per_engine.items()}
        counts = Counter(t for s in token_sets.values() for t in s)
        consensus = {t for t, c in counts.items() if c >= 2} if len(token_sets) > 1 else set()

        lines.append(f"## {name}")
        lines.append("")
        lines.append("| engine | recall | unique | bytes | headings | lists | links | images | "
                     "tables | rows | broken rows | blank runs | trail ws | raw html | ms |")
        lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
        for engine_name, data in per_engine.items():
            text = data["text"]
            s = structure(text)
            h = hygiene(text)
            own = token_sets[engine_name]
            recall = len(own & consensus) / len(consensus) if consensus else 1.0
            unique = len(own - set().union(*[v for k, v in token_sets.items() if k != engine_name]) ) \
                if len(token_sets) > 1 else len(own)
            ms = data["seconds"] * 1000
            if engine_name == "mikromarkdown":
                ms_display = f"{ms:.0f} ({max(0.0, ms - jvm_baseline * 1000):.0f} warm)"
            else:
                ms_display = f"{ms:.0f}"
            lines.append(
                f"| {engine_name} | {recall * 100:.1f}% | {unique} | {len(text)} | {s['headings']} | "
                f"{s['list_items']} | {s['links']} | {s['images']} | {s['tables']} | {s['table_rows']} | "
                f"{s['broken_rows']} | {h['blank_runs']} | {h['trailing_ws']} | {h['raw_html']} | {ms_display} |"
            )
            recall_sum[engine_name].append(recall)
            totals[engine_name].update(s)
            totals[engine_name].update(h)
            totals[engine_name]["files"] += 1
        lines.append("")

    lines.append("## Totals")
    lines.append("")
    lines.append("| engine | files | mean recall | headings | tables | broken rows | blank runs | trail ws |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for engine in engines:
        t = totals[engine.name]
        if not t["files"]:
            continue
        recalls = recall_sum[engine.name]
        mean = sum(recalls) / len(recalls) * 100 if recalls else 0.0
        lines.append(
            f"| {engine.name} | {t['files']} | {mean:.1f}% | {t['headings']} | {t['tables']} | "
            f"{t['broken_rows']} | {t['blank_runs']} | {t['trailing_ws']} |"
        )
    lines.append("")

    report = out_dir / "report.md"
    report.write_text("\n".join(lines))
    print("\n".join(lines))
    print(f"\nwrote {report}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
