"""Canonical logical digests for correction manifests."""
from __future__ import annotations

import hashlib
import json
import sqlite3
from typing import Any


def canonical_json(value: Any) -> str:
    return json.dumps(
        value,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def row_digest(row: dict[str, Any] | None) -> str:
    payload = canonical_json(row).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def compute_content_digest(connection: sqlite3.Connection) -> str:
    from .manifest_source import content_digest_rows

    digest = hashlib.sha256()
    metadata = connection.execute(
        """
        SELECT key, value_json
        FROM manifest_metadata
        WHERE key <> 'content_digest'
        ORDER BY key
        """
    )
    for key, value_json in metadata:
        digest.update(canonical_json(["meta", key, value_json]).encode("utf-8"))
        digest.update(b"\n")
    changes = connection.execute(
        """
        SELECT ordinal, group_kind, group_key, table_name, primary_key, action,
               pre_json, post_json, pre_digest, post_digest
        FROM correction_change
        ORDER BY ordinal
        """
    )
    for row in changes:
        digest.update(canonical_json(["change", *row]).encode("utf-8"))
        digest.update(b"\n")
    for row in content_digest_rows(connection):
        digest.update(canonical_json(row).encode("utf-8"))
        digest.update(b"\n")
    return digest.hexdigest()
