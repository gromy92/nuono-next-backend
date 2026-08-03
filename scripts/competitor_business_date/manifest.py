"""Public immutable correction manifest and scoped-backup interface."""
from __future__ import annotations

from pathlib import Path
from typing import Any

from .manifest_digest import canonical_json, row_digest
from .manifest_reader import ManifestReader
from .manifest_types import (
    SCHEMA_VERSION,
    ManifestChange,
    ManifestError,
    ManifestSeal,
)
from .manifest_writer import ManifestWriter
from .secure_files import SecureFileError, copy_exact_private, sha256_file


def copy_manifest_backup(
    source: Path,
    destination: Path,
    expected_sha256: str,
) -> str:
    try:
        return copy_exact_private(source, destination, expected_sha256)
    except SecureFileError as error:
        raise ManifestError(str(error)) from error


def classify_row_state(
    current: dict[str, Any] | None,
    pre: dict[str, Any] | None,
    post: dict[str, Any] | None,
) -> str:
    if pre is None and current is None:
        return "ABSENT"
    if row_digest(current) == row_digest(post):
        return "POST"
    if row_digest(current) == row_digest(pre):
        return "PRE"
    return "CONFLICT"


__all__ = [
    "SCHEMA_VERSION",
    "ManifestChange",
    "ManifestError",
    "ManifestReader",
    "ManifestSeal",
    "ManifestWriter",
    "canonical_json",
    "classify_row_state",
    "copy_manifest_backup",
    "row_digest",
    "sha256_file",
]
