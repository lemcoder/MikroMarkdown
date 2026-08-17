#!/usr/bin/env python3
"""Build, verify and A/B time the native CLI against the current champion binary.

Usage:
    python3 scripts/optbench.py "label of the change"
    python3 scripts/optbench.py --promote      # current build becomes the champion

Absolute timings drift between sessions — the same binary measured 60 ms one hour and 74 ms the
next — so a change is only ever compared against the champion, interleaved, in the same run.
Output is verified against the recorded baselines first: a change that alters what we produce is
reported as broken rather than as fast.
"""
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
JVM = REPO / "cli/build/install/cli/bin/cli"
NATIVE = REPO / "cli-native/build/bin/macosArm64/releaseExecutable/cli-native.kexe"
CHAMPION = REPO / "build/perf/champion.kexe"
CHAMPION_JVM = REPO / "build/perf/champion-cli"
FIXTURES = REPO / "library/src/commonTest/resources/test_files"
BASELINES = REPO / "build/benchmark"
PERF = REPO / "build/perf"

# Native only runs CSV; the JVM champion covers the document formats it cannot.
TIMED = [("580 KB", PERF / "medium.csv"), ("1.8 MB", PERF / "big.csv")]
TIMED_JVM = [
    ("wiki", FIXTURES / "test_wikipedia.html"),
    ("epub", FIXTURES / "test.epub"),
    ("pdf", FIXTURES / "test.pdf"),
    ("json", PERF / "big.json"),
]
ROUNDS = 7


def build():
    result = subprocess.run(
        ["./gradlew", ":cli:installDist", ":cli-native:linkReleaseExecutableMacosArm64",
         "--no-configuration-cache", "-q"],
        capture_output=True, text=True, cwd=REPO,
    )
    errors = [line for line in (result.stdout + result.stderr).splitlines() if line.startswith("e:")]
    return result.returncode == 0, errors[:3]


def verify():
    problems = []
    for baseline in sorted(BASELINES.glob("mikromarkdown_*.md")):
        name = baseline.name.replace("mikromarkdown_", "").removesuffix(".md")
        fixture = FIXTURES / name
        if not fixture.exists():
            continue
        if subprocess.run([str(JVM), str(fixture)], capture_output=True, cwd=REPO).stdout != baseline.read_bytes():
            problems.append(f"jvm {name}")
    for name in ("test.csv", "test.json", "test.xml", "test_blog.html", "test_wikipedia.html", "test.epub"):
        fixture = FIXTURES / name
        jvm = subprocess.run([str(JVM), str(fixture)], capture_output=True, cwd=REPO).stdout
        native = subprocess.run([str(NATIVE), str(fixture)], capture_output=True, cwd=REPO).stdout
        if jvm != native:
            problems.append(f"native != jvm {name}")
    big = PERF / "big.csv"
    if big.exists():
        jvm = subprocess.run([str(JVM), str(big)], capture_output=True, cwd=REPO).stdout
        native = subprocess.run([str(NATIVE), str(big)], capture_output=True, cwd=REPO).stdout
        if jvm != native:
            problems.append("native != jvm big.csv")
    return problems


def interleaved(path, champion=None, candidate=None):
    """Alternate champion and candidate so drift hits both equally. Returns (champion, candidate)."""
    champion = champion or CHAMPION
    candidate = candidate or NATIVE
    # A copied distribution cannot use its CDS archive: the archive records absolute classpaths, and
    # the JVM drops it without a word. Both sides run without it so the comparison is of the code.
    environment = dict(os.environ, MIKROMARKDOWN_NO_CDS="1")
    champion_times, candidate_times = [], []
    for _ in range(ROUNDS):
        for binary, times in ((champion, champion_times), (candidate, candidate_times)):
            start = time.perf_counter()
            subprocess.run([str(binary), str(path)], capture_output=True, cwd=REPO, env=environment)
            times.append((time.perf_counter() - start) * 1000)
    return min(champion_times), min(candidate_times)


def main():
    if "--promote" in sys.argv:
        CHAMPION.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(NATIVE, CHAMPION)
        if CHAMPION_JVM.exists():
            shutil.rmtree(CHAMPION_JVM)
        shutil.copytree(JVM.parent.parent, CHAMPION_JVM)
        print("champion updated (native binary and JVM distribution)")
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

    champion_cli = CHAMPION_JVM / "bin/cli"
    if champion_cli.exists():
        for name, path in TIMED_JVM:
            if not path.exists():
                continue
            champion, candidate = interleaved(path, champion=champion_cli, candidate=JVM)
            delta = (candidate - champion) / champion * 100
            parts.append(f"{name}: {champion:.0f} -> {candidate:.0f} ({delta:+.0f}%)")
    print(f"{label}: " + " | ".join(parts))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
