"""Shared immutable manifest value types."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


SCHEMA_VERSION = 1


class ManifestError(RuntimeError):
    pass


@dataclass(frozen=True)
class ManifestSeal:
    path: Path
    file_sha256: str
    content_digest: str


@dataclass(frozen=True)
class ManifestChange:
    ordinal: int
    group_kind: str
    group_key: str
    table_name: str
    primary_key: str
    action: str
    pre: dict[str, Any] | None
    post: dict[str, Any] | None
    pre_digest: str
    post_digest: str
