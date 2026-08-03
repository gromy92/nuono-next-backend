"""Disk-backed staging for large read-only MySQL extracts."""
from __future__ import annotations

import json
import hashlib
import os
import sqlite3
from pathlib import Path
from typing import Any, Callable, Iterable, Iterator

from .manifest import canonical_json


KINDS = frozenset(
    {
        "schema_fingerprint",
        "event_sequence",
        "snapshot",
        "event",
        "rank",
        "keyword_run",
    }
)


class SourceStageError(RuntimeError):
    pass


class SourceStage:
    def __init__(self, path: Path):
        target = Path(path)
        flags = os.O_CREAT | os.O_EXCL | os.O_WRONLY
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = os.open(target, flags, 0o600)
        os.close(descriptor)
        self.path = target.resolve()
        self.connection = sqlite3.connect(self.path)
        self.connection.executescript(
            """
            PRAGMA journal_mode=DELETE;
            PRAGMA synchronous=FULL;
            CREATE TABLE source_row (
                kind TEXT NOT NULL,
                group_key TEXT NOT NULL,
                row_key TEXT NOT NULL,
                row_json TEXT NOT NULL,
                PRIMARY KEY(kind, row_key)
            ) STRICT;
            CREATE INDEX idx_source_group
                ON source_row(kind, group_key, row_key);
            """
        )

    def ingest(
        self,
        kind: str,
        rows: Iterable[dict[str, Any]],
        *,
        group_key: Callable[[dict[str, Any]], str],
        row_key: Callable[[dict[str, Any]], str],
    ) -> int:
        if kind not in KINDS:
            raise SourceStageError(f"unsupported source kind: {kind}")
        count = 0
        try:
            for row in rows:
                self.connection.execute(
                    """
                    INSERT INTO source_row(kind, group_key, row_key, row_json)
                    VALUES (?, ?, ?, ?)
                    """,
                    (
                        kind,
                        str(group_key(row)),
                        str(row_key(row)),
                        canonical_json(row),
                    ),
                )
                count += 1
                if count % 1000 == 0:
                    self.connection.commit()
            self.connection.commit()
        except sqlite3.IntegrityError as error:
            self.connection.rollback()
            raise SourceStageError(
                f"duplicate or invalid {kind} row in frozen source"
            ) from error
        return count

    def group_keys(self, kind: str) -> Iterator[str]:
        rows = self.connection.execute(
            """
            SELECT DISTINCT group_key
            FROM source_row
            WHERE kind = ?
            ORDER BY group_key
            """,
            (kind,),
        )
        yield from (row[0] for row in rows)

    def rows(self, kind: str, group_key: str) -> Iterator[dict[str, Any]]:
        rows = self.connection.execute(
            """
            SELECT row_json
            FROM source_row
            WHERE kind = ? AND group_key = ?
            ORDER BY row_key
            """,
            (kind, str(group_key)),
        )
        for (payload,) in rows:
            row = json.loads(payload)
            if not isinstance(row, dict):
                raise SourceStageError("staged source payload is not an object")
            yield row

    def one(self, kind: str, group_key: str) -> dict[str, Any]:
        rows = list(self.rows(kind, group_key))
        if len(rows) != 1:
            raise SourceStageError(
                f"expected one {kind} row for {group_key}, found {len(rows)}"
            )
        return rows[0]

    def count(self, kind: str) -> int:
        return int(
            self.connection.execute(
                "SELECT COUNT(*) FROM source_row WHERE kind = ?",
                (kind,),
            ).fetchone()[0]
        )

    def has_row(self, kind: str, row_key: str) -> bool:
        return (
            self.connection.execute(
                "SELECT 1 FROM source_row WHERE kind = ? AND row_key = ?",
                (kind, row_key),
            ).fetchone()
            is not None
        )

    def row_by_key(self, kind: str, row_key: str) -> dict[str, Any]:
        record = self.connection.execute(
            "SELECT row_json FROM source_row WHERE kind = ? AND row_key = ?",
            (kind, row_key),
        ).fetchone()
        if record is None:
            raise SourceStageError(f"missing {kind} source row {row_key}")
        value = json.loads(record[0])
        if not isinstance(value, dict):
            raise SourceStageError("staged source payload is not an object")
        return value

    def content_digest(self) -> str:
        digest = hashlib.sha256()
        rows = self.connection.execute(
            """
            SELECT kind, group_key, row_key, row_json
            FROM source_row
            ORDER BY kind, group_key, row_key
            """
        )
        for row in rows:
            digest.update(canonical_json(list(row)).encode("utf-8"))
            digest.update(b"\n")
        return digest.hexdigest()

    def iter_rows(
        self,
        kind: str | None = None,
    ) -> Iterator[tuple[str, str, str, dict[str, Any]]]:
        if kind is not None and kind not in KINDS:
            raise SourceStageError(f"unsupported source kind: {kind}")
        where = "WHERE kind = ?" if kind is not None else ""
        values = (kind,) if kind is not None else ()
        rows = self.connection.execute(
            f"""
            SELECT kind, group_key, row_key, row_json
            FROM source_row
            {where}
            ORDER BY kind, group_key, row_key
            """,
            values,
        )
        for row_kind, group_key, row_key, payload in rows:
            value = json.loads(payload)
            if not isinstance(value, dict):
                raise SourceStageError("staged source payload is not an object")
            yield row_kind, group_key, row_key, value

    def close(self) -> None:
        self.connection.close()

    def __enter__(self) -> "SourceStage":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()
