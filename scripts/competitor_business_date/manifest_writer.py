"""Private SQLite writer for immutable correction manifests."""
from __future__ import annotations

import os
import sqlite3
from pathlib import Path
from typing import Any

from .manifest_digest import canonical_json, compute_content_digest, row_digest
from .manifest_source import SOURCE_SCHEMA_SQL, add_resolution, add_source
from .manifest_types import ManifestError, ManifestSeal, SCHEMA_VERSION
from .secure_files import (
    SecureFileError,
    create_exclusive,
    fsync_directory,
    fsync_file,
    sha256_file,
)


class ManifestWriter:
    def __init__(self, path: Path, metadata: dict[str, Any]):
        try:
            self.path = create_exclusive(Path(path))
        except SecureFileError as error:
            raise ManifestError(str(error)) from error
        self._sealed = False
        self._connection = sqlite3.connect(self.path)
        self._connection.executescript(
            """
            PRAGMA journal_mode=DELETE;
            PRAGMA synchronous=FULL;
            CREATE TABLE manifest_metadata (
                key TEXT PRIMARY KEY,
                value_json TEXT NOT NULL
            ) STRICT;
            CREATE TABLE correction_change (
                ordinal INTEGER PRIMARY KEY AUTOINCREMENT,
                group_kind TEXT NOT NULL,
                group_key TEXT NOT NULL,
                table_name TEXT NOT NULL,
                primary_key TEXT NOT NULL,
                action TEXT NOT NULL CHECK(action IN ('UPDATE', 'INSERT')),
                pre_json TEXT NOT NULL,
                post_json TEXT NOT NULL,
                pre_digest TEXT NOT NULL,
                post_digest TEXT NOT NULL,
                UNIQUE(table_name, primary_key)
            ) STRICT;
            CREATE INDEX idx_correction_change_group
                ON correction_change(group_kind, group_key, ordinal);
            """
            + SOURCE_SCHEMA_SQL
        )
        values = dict(metadata)
        values.setdefault("schema_version", SCHEMA_VERSION)
        for key, value in sorted(values.items()):
            self._connection.execute(
                "INSERT INTO manifest_metadata(key, value_json) VALUES (?, ?)",
                (key, canonical_json(value)),
            )
        self._connection.commit()

    def set_metadata(self, key: str, value: Any) -> None:
        if self._sealed:
            raise ManifestError("manifest is already sealed")
        if key == "content_digest":
            raise ManifestError("content digest is reserved")
        self._connection.execute(
            """
            INSERT INTO manifest_metadata(key, value_json) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value_json = excluded.value_json
            """,
            (key, canonical_json(value)),
        )

    def add_change(
        self,
        *,
        group_kind: str,
        group_key: str,
        table_name: str,
        primary_key: str,
        action: str,
        pre: dict[str, Any] | None,
        post: dict[str, Any] | None,
    ) -> None:
        if self._sealed:
            raise ManifestError("manifest is already sealed")
        normalized_action = action.upper()
        if normalized_action == "UPDATE" and (pre is None or post is None):
            raise ManifestError("UPDATE changes require PRE and POST rows")
        if normalized_action == "INSERT" and (pre is not None or post is None):
            raise ManifestError("INSERT changes require absent PRE and present POST")
        self._connection.execute(
            """
            INSERT INTO correction_change(
                group_kind, group_key, table_name, primary_key, action,
                pre_json, post_json, pre_digest, post_digest
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                group_kind,
                group_key,
                table_name,
                str(primary_key),
                normalized_action,
                canonical_json(pre),
                canonical_json(post),
                row_digest(pre),
                row_digest(post),
            ),
        )

    def add_source_row(
        self,
        *,
        kind: str,
        group_key: str,
        row_key: str,
        row: dict[str, Any],
    ) -> None:
        self._require_open()
        add_source(
            self._connection,
            kind=kind,
            group_key=str(group_key),
            row_key=str(row_key),
            row=dict(row),
        )

    def add_resolution(
        self,
        *,
        kind: str,
        group_key: str,
        resolution: dict[str, Any],
    ) -> None:
        self._require_open()
        add_resolution(
            self._connection,
            kind=kind,
            group_key=str(group_key),
            resolution=dict(resolution),
        )

    def seal(self) -> ManifestSeal:
        self._require_open()
        content_digest = compute_content_digest(self._connection)
        self._connection.execute(
            "INSERT INTO manifest_metadata(key, value_json) VALUES (?, ?)",
            ("content_digest", canonical_json(content_digest)),
        )
        self._connection.commit()
        self._connection.execute("PRAGMA optimize")
        self._connection.close()
        os.chmod(self.path, 0o400)
        fsync_file(self.path)
        fsync_directory(self.path.parent)
        self._sealed = True
        return ManifestSeal(self.path, sha256_file(self.path), content_digest)

    def abort(self) -> None:
        if not self._sealed:
            self._connection.close()
            self._sealed = True

    def _require_open(self) -> None:
        if self._sealed:
            raise ManifestError("manifest is already sealed")
