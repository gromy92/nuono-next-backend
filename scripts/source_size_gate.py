#!/usr/bin/env python3
"""Enforce the shrinking source-file size baseline."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path, PurePosixPath
from typing import Iterable


MAX_LINES = 300
BASELINE_RELATIVE = PurePosixPath(".github/source-size-baseline")
SOURCE_SUFFIXES = frozenset(
    {
        ".java",
        ".xml",
        ".yml",
        ".yaml",
        ".properties",
        ".js",
        ".mjs",
        ".cjs",
        ".jsx",
        ".ts",
        ".tsx",
        ".css",
        ".scss",
        ".sass",
        ".less",
        ".html",
        ".htm",
        ".py",
        ".sh",
        ".bash",
        ".zsh",
        ".cmd",
        ".bat",
        ".ps1",
        ".sql",
    }
)
EXCLUDED_PREFIXES = (
    "src/main/resources/static/assets/",
    "target/",
    "node_modules/",
    "dist/",
    "build/",
)


class GateError(RuntimeError):
    """Raised when repository metadata cannot be inspected safely."""


def run_git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    command = ["git", "-C", str(root), *args]
    result = subprocess.run(command, capture_output=True, check=False)
    if check and result.returncode != 0:
        detail = result.stderr.decode("utf-8", "replace").strip()
        raise GateError(f"{' '.join(command)} failed: {detail}")
    return result


def is_source(path: str) -> bool:
    normalized = PurePosixPath(path).as_posix()
    if normalized.startswith(EXCLUDED_PREFIXES):
        return False
    source_path = PurePosixPath(normalized)
    name = source_path.name.lower()
    executable_config = (
        name.startswith(".env")
        or name.startswith("dockerfile")
        or name == "makefile"
        or name in {"package.json", "pnpm-workspace.yaml"}
        or ("compose" in name and source_path.suffix.lower() in {".yml", ".yaml"})
        or ".config." in name
        or name.startswith(("jsconfig.", "tsconfig."))
    )
    return executable_config or source_path.suffix.lower() in SOURCE_SUFFIXES


def physical_line_count(data: bytes) -> int:
    """Count physical lines, including blank and comment-only lines."""
    return len(data.splitlines())


def repository_source_counts(root: Path) -> dict[str, int]:
    result = run_git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z")
    counts: dict[str, int] = {}
    for raw_path in result.stdout.split(b"\0"):
        if not raw_path:
            continue
        path = raw_path.decode("utf-8", "surrogateescape")
        full_path = root / path
        source = is_source(path)
        if not source and full_path.is_file():
            with full_path.open("rb") as stream:
                source = stream.read(2) == b"#!"
        if source and full_path.is_file():
            counts[path] = physical_line_count(full_path.read_bytes())
    return counts


def parse_baseline_lines(lines: Iterable[str], source: str) -> dict[str, int]:
    entries: dict[str, int] = {}
    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            raise GateError(f"{source}:{line_number}: expected path<TAB>ceiling")
        path, raw_ceiling = parts
        try:
            ceiling = int(raw_ceiling)
        except ValueError as error:
            raise GateError(f"{source}:{line_number}: ceiling must be an integer") from error
        if ceiling <= MAX_LINES:
            raise GateError(f"{source}:{line_number}: ceiling must exceed {MAX_LINES}")
        if path in entries:
            raise GateError(f"{source}:{line_number}: duplicate baseline path: {path}")
        baseline_path = PurePosixPath(path)
        if baseline_path.is_absolute() or ".." in baseline_path.parts:
            raise GateError(f"{source}:{line_number}: invalid baseline path: {path}")
        if baseline_path.as_posix().startswith(EXCLUDED_PREFIXES):
            raise GateError(f"{source}:{line_number}: unsupported or excluded source path: {path}")
        entries[path] = ceiling
    return entries


def merge_baseline(entries: dict[str, int], addition: dict[str, int], source: str) -> None:
    duplicates = sorted(entries.keys() & addition.keys())
    if duplicates:
        raise GateError(f"{source}: duplicate paths across baseline shards: {', '.join(duplicates)}")
    entries.update(addition)


def current_baseline(root: Path) -> dict[str, int]:
    directory = root / BASELINE_RELATIVE
    entries: dict[str, int] = {}
    for path in sorted(directory.glob("*.tsv")) if directory.is_dir() else []:
        data = path.read_bytes()
        shard_lines = physical_line_count(data)
        if shard_lines > MAX_LINES:
            raise GateError(f"{path}: baseline shard has {shard_lines} lines; split it")
        parsed = parse_baseline_lines(data.decode("utf-8").splitlines(), str(path))
        merge_baseline(entries, parsed, str(path))
    return entries


def baseline_at_ref(root: Path, ref: str) -> tuple[dict[str, int], bool]:
    if not ref or set(ref) == {"0"}:
        return {}, False
    listing = run_git(
        root,
        "ls-tree",
        "-r",
        "--name-only",
        ref,
        "--",
        BASELINE_RELATIVE.as_posix(),
    )
    shard_paths = [
        line.decode("utf-8", "surrogateescape")
        for line in listing.stdout.splitlines()
        if line.endswith(b".tsv")
    ]
    if not shard_paths:
        return {}, False
    entries: dict[str, int] = {}
    for path in sorted(shard_paths):
        result = run_git(root, "show", f"{ref}:{path}")
        text = result.stdout.decode("utf-8")
        parsed = parse_baseline_lines(text.splitlines(), f"{ref}:{path}")
        merge_baseline(entries, parsed, f"{ref}:{path}")
    return entries, True


def source_counts_at_ref(root: Path, ref: str, paths: Iterable[str]) -> dict[str, int | None]:
    """Read selected source sizes from a baseline commit without checking it out."""
    counts: dict[str, int | None] = {}
    for path in sorted(paths):
        result = run_git(root, "show", f"{ref}:{path}", check=False)
        counts[path] = physical_line_count(result.stdout) if result.returncode == 0 else None
    return counts


def policy_errors(
    counts: dict[str, int],
    baseline: dict[str, int],
    base_baseline: dict[str, int],
    base_has_baseline: bool,
    bootstrap_base_counts: dict[str, int | None] | None = None,
) -> list[str]:
    errors: list[str] = []
    oversized = {path: lines for path, lines in counts.items() if lines > MAX_LINES}

    for path, lines in sorted(oversized.items()):
        ceiling = baseline.get(path)
        if ceiling is None:
            kind = "new oversized file" if base_has_baseline else "missing bootstrap entry"
            errors.append(f"{path}: {kind}; {lines} lines exceeds {MAX_LINES}")
        elif lines > ceiling:
            errors.append(f"{path}: grew to {lines} lines above ceiling {ceiling}")
        elif lines < ceiling:
            errors.append(f"{path}: shrank to {lines} lines; lower its ceiling from {ceiling}")

    for path, ceiling in sorted(baseline.items()):
        lines = counts.get(path)
        if lines is None:
            errors.append(f"{path}: deleted file still has baseline ceiling {ceiling}; remove the entry")
        elif lines <= MAX_LINES:
            errors.append(f"{path}: now {lines} lines; remove the obsolete baseline entry")

    if not base_has_baseline and bootstrap_base_counts is not None:
        for path, lines in sorted(oversized.items()):
            base_lines = bootstrap_base_counts.get(path)
            if base_lines is None or base_lines <= MAX_LINES:
                errors.append(f"{path}: new oversized file is not eligible for bootstrap")
            elif lines > base_lines:
                errors.append(
                    f"{path}: grew from {base_lines} to {lines} lines during bootstrap"
                )

    if base_has_baseline:
        for path in sorted(baseline.keys() - base_baseline.keys()):
            errors.append(f"{path}: baseline entry is new; new debt and renames are forbidden")
        for path in sorted(baseline.keys() & base_baseline.keys()):
            if baseline[path] > base_baseline[path]:
                errors.append(
                    f"{path}: ceiling increased from {base_baseline[path]} to {baseline[path]}"
                )
    return errors


def category_counts(counts: dict[str, int]) -> dict[str, int]:
    result = {"main": 0, "test": 0, "sql": 0, "other": 0}
    for path, lines in counts.items():
        if lines <= MAX_LINES:
            continue
        if path.endswith(".sql"):
            result["sql"] += 1
        elif path.startswith("src/main/java/"):
            result["main"] += 1
        elif path.startswith("src/test/java/"):
            result["test"] += 1
        else:
            result["other"] += 1
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--base-ref", default=os.environ.get("SOURCE_SIZE_BASE_REF", "HEAD"))
    args = parser.parse_args(argv)
    root = args.root.resolve()
    try:
        counts = repository_source_counts(root)
        baseline = current_baseline(root)
        base_baseline, base_has_baseline = baseline_at_ref(root, args.base_ref)
        bootstrap_counts = None
        if not base_has_baseline and args.base_ref and set(args.base_ref) != {"0"}:
            oversized_paths = (path for path, lines in counts.items() if lines > MAX_LINES)
            bootstrap_counts = source_counts_at_ref(root, args.base_ref, oversized_paths)
        errors = policy_errors(
            counts,
            baseline,
            base_baseline,
            base_has_baseline,
            bootstrap_counts,
        )
    except (GateError, OSError, UnicodeError) as error:
        print(f"source-size gate error: {error}", file=sys.stderr)
        return 2

    summary = category_counts(counts)
    oversized_total = sum(summary.values())
    mode = "enforce" if base_has_baseline else "bootstrap"
    print(
        "source-size gate: "
        f"mode={mode}, oversized={oversized_total}, baseline={len(baseline)}, "
        f"main={summary['main']}, test={summary['test']}, sql={summary['sql']}, "
        f"other={summary['other']}"
    )
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"source-size gate failed with {len(errors)} violation(s)", file=sys.stderr)
        return 1
    print("source-size gate passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
