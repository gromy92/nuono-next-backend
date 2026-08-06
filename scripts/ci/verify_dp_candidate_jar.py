#!/usr/bin/env python3
"""Fail closed when the packaged DP candidate does not match its release boundary."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import sys
import zipfile


REQUIRED_ENTRIES = frozenset(
    {
        "BOOT-INF/classes/META-INF/nuono/dp-runtime-cutover-manifest-v1",
        "BOOT-INF/classes/META-INF/nuono/dp-report-download-probe-v1",
        "BOOT-INF/classes/META-INF/nuono/dp10-openapi-probe-v1",
    }
)
FORBIDDEN_ENTRIES = frozenset(
    {
        "BOOT-INF/classes/com/nuono/next/procurement/aliorder/"
        "FakeAli1688HistoricalOrderProvider.class",
    }
)
ROOT_LEVEL_MARKERS = frozenset(
    entry.removeprefix("BOOT-INF/classes/") for entry in REQUIRED_ENTRIES
)
FORBIDDEN_PREFIXES = (
    "BOOT-INF/classes/com/nuono/next/noonreadiness/",
    "BOOT-INF/classes/com/nuono/next/nooncompleteness/",
    "BOOT-INF/classes/com/nuono/next/noonsync/",
)


def verify(candidate: Path) -> str:
    if not candidate.is_file():
        raise ValueError("DP_CANDIDATE_JAR_MISSING")
    try:
        with zipfile.ZipFile(candidate) as archive:
            entries = frozenset(archive.namelist())
    except (OSError, zipfile.BadZipFile) as failure:
        raise ValueError("DP_CANDIDATE_JAR_INVALID") from failure

    missing = sorted(REQUIRED_ENTRIES - entries)
    if missing:
        raise ValueError("DP_CANDIDATE_MARKER_MISSING:" + ",".join(missing))
    misplaced = sorted(ROOT_LEVEL_MARKERS & entries)
    if misplaced:
        raise ValueError("DP_CANDIDATE_MARKER_OUTSIDE_CLASSPATH:" + ",".join(misplaced))
    forbidden = sorted(FORBIDDEN_ENTRIES & entries)
    forbidden.extend(
        sorted(
            entry
            for entry in entries
            if any(entry.startswith(prefix) for prefix in FORBIDDEN_PREFIXES)
        )
    )
    if forbidden:
        raise ValueError("DP_CANDIDATE_RETIRED_SURFACE_PRESENT:" + ",".join(forbidden))
    return hashlib.sha256(candidate.read_bytes()).hexdigest()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, type=Path)
    options = parser.parse_args(argv)
    try:
        digest = verify(options.jar)
    except ValueError as failure:
        print(str(failure), file=sys.stderr)
        return 1
    print(f"DP candidate Jar verified: sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
