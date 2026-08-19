#!/usr/bin/env python3
"""Build, verify and A/B time the native CLI against the current champion binary.

Usage:
    python3 scripts/optbench.py "label of the change"
    python3 scripts/optbench.py --promote      # current build becomes the champion

Absolute timings drift between sessions — the same binary measured 60 ms one hour and 74 ms the
next — so a change is only ever compared against the champion, interleaved, in the same run.

Output is verified against the recorded baselines first: a change that alters what we produce is
reported as broken rather than as fast. Those baselines were recorded when a JVM CLI still existed
and matched it byte for byte, so they remain the reference for what each format should produce.
"""
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
NATIVE = REPO / "cli-native/build/bin/macosArm64/releaseExecutable/cli-native.kexe"
CHAMPION = REPO / "build/perf/champion.kexe"
FIXTURES = REPO / "library/src/commonTest/resources/test_files"
BASELINES = REPO / "build/benchmark"
PERF = REPO / "build/perf"

TIMED = [
    ("580 KB", PERF / "medium.csv"),
    ("1.8 MB", PERF / "big.csv"),
    ("wiki", FIXTURES / "test_wikipedia.html"),
    ("epub", FIXTURES / "test.epub"),
    ("json", PERF / "big.json"),
]

# PDF is deliberately not pinned: pdfium reads a document differently from the PDFBox that recorded
# the baselines, and that difference is documented rather than frozen.
UNVERIFIED = {"test.pdf"}

ROUNDS = 7


def build():
    result = subprocess.run(
        ["./gradlew", ":cli-native:linkReleaseExecutableMacosArm64", "--no-configuration-cache", "-q"],
        capture_output=True, text=True, cwd=REPO,
    )
    errors = [line for line in (result.stdout + result.stderr).splitlines() if line.startswith("e:")]
    return result.returncode == 0, errors[:3]


def verify():
    problems = []
    for baseline in sorted(BASELINES.glob("mikromarkdown_*.md")):
        name = baseline.name.replace("mikromarkdown_", "").removesuffix(".md")
        fixture = FIXTURES / name
        if not fixture.exists() or name in UNVERIFIED:
            continue
        produced = subprocess.run([str(NATIVE), str(fixture)], capture_output=True, cwd=REPO)
        if produced.returncode != 0:
            problems.append(f"{name} failed to convert")
        elif produced.stdout != baseline.read_bytes():
            problems.append(name)
    return problems


def interleaved(path):
    """Alternate champion and candidate so drift hits both equally. Returns (champion, candidate)."""
    champion_times, candidate_times = [], []
    for _ in range(ROUNDS):
        for binary, times in ((CHAMPION, champion_times), (NATIVE, candidate_times)):
            start = time.perf_counter()
            subprocess.run([str(binary), str(path)], capture_output=True, cwd=REPO)
            times.append((time.perf_counter() - start) * 1000)
    return min(champion_times), min(candidate_times)


def main():
    if "--promote" in sys.argv:
        CHAMPION.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(NATIVE, CHAMPION)
        print("champion updated")
        return 0

    label = sys.argv[1] if len(sys.argv) > 1 else "unlabelled"
    ok, errors = build()
    if not ok:
        print(f"{label}: BUILD FAILED")
        for error in errors:
            print(f"  {error}")
        return 1

    problems = verify()
    if problems:
        print(f"{label}: OUTPUT CHANGED -> {', '.join(problems)}")
        return 2

    if not CHAMPION.exists():
        print(f"{label}: no champion yet — run --promote first")
        return 3

    parts = []
    for name, path in TIMED:
        if not path.exists():
            continue
        champion, candidate = interleaved(path)
        delta = (candidate - champion) / champion * 100
        parts.append(f"{name}: {champion:.0f} -> {candidate:.0f} ({delta:+.0f}%)")
    print(f"{label}: " + " | ".join(parts))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
