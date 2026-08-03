"""Immutable, digest-verifying correction manifest reader."""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Iterator
from urllib.parse import quote

from .manifest_digest import compute_content_digest
from .manifest_source import (
    PlanningResolution,
    SourceEvidence,
    iter_resolutions,
    iter_source,
    source_content_digest,
    source_counts,
)
from .manifest_types import ManifestChange, ManifestError, SCHEMA_VERSION
from .secure_files import SecureFileError, require_private_regular_file, sha256_file


class ManifestReader:
    def __init__(self, path: Path, expected_sha256: str):
        try:
            self.path = require_private_regular_file(Path(path))
        except SecureFileError as error:
            raise ManifestError(str(error)) from error
        if len(expected_sha256) != 64 or sha256_file(self.path) != expected_sha256:
            raise ManifestError("manifest SHA-256 mismatch")
        uri = f"file:{quote(str(self.path))}?mode=ro&immutable=1"
        self._connection = sqlite3.connect(uri, uri=True)
        self.metadata = {
            key: json.loads(value)
            for key, value in self._connection.execute(
                "SELECT key, value_json FROM manifest_metadata"
            )
        }
        if self.metadata.get("schema_version") != SCHEMA_VERSION:
            self.close()
            raise ManifestError("unsupported manifest schema version")
        if self.compute_content_digest() != self.metadata.get("content_digest"):
            self.close()
            raise ManifestError("manifest content digest mismatch")

    def compute_content_digest(self) -> str:
        return compute_content_digest(self._connection)

    def iter_changes(
        self,
        *,
        group_kind: str | None = None,
        group_key: str | None = None,
    ) -> Iterator[ManifestChange]:
        clauses: list[str] = []
        values: list[str] = []
        if group_kind is not None:
            clauses.append("group_kind = ?")
            values.append(group_kind)
        if group_key is not None:
            clauses.append("group_key = ?")
            values.append(group_key)
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        rows = self._connection.execute(
            f"""
            SELECT ordinal, group_kind, group_key, table_name, primary_key,
                   action, pre_json, post_json, pre_digest, post_digest
            FROM correction_change
            {where}
            ORDER BY ordinal
            """,
            values,
        )
        for row in rows:
            yield ManifestChange(
                *row[:6],
                json.loads(row[6]),
                json.loads(row[7]),
                row[8],
                row[9],
            )

    def group_keys(self) -> tuple[tuple[str, str], ...]:
        rows = self._connection.execute(
            """
            SELECT group_kind, group_key
            FROM correction_change
            GROUP BY group_kind, group_key
            ORDER BY MIN(ordinal)
            """
        )
        return tuple((row[0], row[1]) for row in rows)

    def change_count(self) -> int:
        return int(
            self._connection.execute(
                "SELECT COUNT(*) FROM correction_change"
            ).fetchone()[0]
        )

    def iter_source_rows(self) -> Iterator[SourceEvidence]:
        yield from iter_source(self._connection)

    def iter_resolutions(self) -> Iterator[PlanningResolution]:
        yield from iter_resolutions(self._connection)

    def source_counts(self) -> dict[str, int]:
        return source_counts(self._connection)

    def source_content_digest(self) -> str:
        return source_content_digest(self._connection)

    def close(self) -> None:
        self._connection.close()

    def __enter__(self) -> "ManifestReader":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()
