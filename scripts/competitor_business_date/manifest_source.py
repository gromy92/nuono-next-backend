"""Immutable source evidence and planning-resolution rows in a manifest."""
from __future__ import annotations

import hashlib
import json
import sqlite3
from dataclasses import dataclass
from typing import Any, Iterator

from .manifest_digest import canonical_json


SOURCE_SCHEMA_SQL = """
CREATE TABLE scoped_source_row (
    kind TEXT NOT NULL,
    group_key TEXT NOT NULL,
    row_key TEXT NOT NULL,
    row_json TEXT NOT NULL,
    row_digest TEXT NOT NULL,
    PRIMARY KEY(kind, row_key)
) STRICT;
CREATE INDEX idx_scoped_source_group
    ON scoped_source_row(kind, group_key, row_key);
CREATE TABLE planning_resolution (
    kind TEXT NOT NULL,
    group_key TEXT NOT NULL,
    resolution_json TEXT NOT NULL,
    resolution_digest TEXT NOT NULL,
    PRIMARY KEY(kind, group_key)
) STRICT;
"""


@dataclass(frozen=True)
class SourceEvidence:
    kind: str
    group_key: str
    row_key: str
    row: dict[str, Any]
    row_digest: str


@dataclass(frozen=True)
class PlanningResolution:
    kind: str
    group_key: str
    resolution: dict[str, Any]
    resolution_digest: str


def add_source(
    connection: sqlite3.Connection,
    *,
    kind: str,
    group_key: str,
    row_key: str,
    row: dict[str, Any],
) -> None:
    payload = canonical_json(row)
    connection.execute(
        """
        INSERT INTO scoped_source_row(
            kind, group_key, row_key, row_json, row_digest
        ) VALUES (?, ?, ?, ?, ?)
        """,
        (kind, group_key, row_key, payload, _digest(payload)),
    )


def add_resolution(
    connection: sqlite3.Connection,
    *,
    kind: str,
    group_key: str,
    resolution: dict[str, Any],
) -> None:
    payload = canonical_json(resolution)
    connection.execute(
        """
        INSERT INTO planning_resolution(
            kind, group_key, resolution_json, resolution_digest
        ) VALUES (?, ?, ?, ?)
        """,
        (kind, group_key, payload, _digest(payload)),
    )


def iter_source(connection: sqlite3.Connection) -> Iterator[SourceEvidence]:
    rows = connection.execute(
        """
        SELECT kind, group_key, row_key, row_json, row_digest
        FROM scoped_source_row
        ORDER BY kind, group_key, row_key
        """
    )
    for kind, group_key, row_key, payload, digest in rows:
        value = json.loads(payload)
        if not isinstance(value, dict) or _digest(payload) != digest:
            raise ValueError("scoped source evidence is invalid")
        yield SourceEvidence(kind, group_key, row_key, value, digest)


def iter_resolutions(
    connection: sqlite3.Connection,
) -> Iterator[PlanningResolution]:
    rows = connection.execute(
        """
        SELECT kind, group_key, resolution_json, resolution_digest
        FROM planning_resolution
        ORDER BY kind, group_key
        """
    )
    for kind, group_key, payload, digest in rows:
        value = json.loads(payload)
        if not isinstance(value, dict) or _digest(payload) != digest:
            raise ValueError("planning resolution evidence is invalid")
        yield PlanningResolution(kind, group_key, value, digest)


def source_counts(connection: sqlite3.Connection) -> dict[str, int]:
    return {
        str(kind): int(count)
        for kind, count in connection.execute(
            """
            SELECT kind, COUNT(*)
            FROM scoped_source_row
            GROUP BY kind
            ORDER BY kind
            """
        )
    }


def source_content_digest(connection: sqlite3.Connection) -> str:
    digest = hashlib.sha256()
    rows = connection.execute(
        """
        SELECT kind, group_key, row_key, row_json
        FROM scoped_source_row
        ORDER BY kind, group_key, row_key
        """
    )
    for row in rows:
        digest.update(canonical_json(list(row)).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()


def content_digest_rows(connection: sqlite3.Connection) -> Iterator[list[Any]]:
    for row in connection.execute(
        """
        SELECT kind, group_key, row_key, row_json, row_digest
        FROM scoped_source_row
        ORDER BY kind, group_key, row_key
        """
    ):
        yield ["source", *row]
    for row in connection.execute(
        """
        SELECT kind, group_key, resolution_json, resolution_digest
        FROM planning_resolution
        ORDER BY kind, group_key
        """
    ):
        yield ["resolution", *row]


def _digest(payload: str) -> str:
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()
