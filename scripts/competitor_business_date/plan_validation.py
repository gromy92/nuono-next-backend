"""Validate a sealed plan completely before exposing its final path."""
from __future__ import annotations

from pathlib import Path

from .apply_sql import APPLY
from .evidence_contract import validate_source_evidence
from .execution_plan import (
    ordered_groups,
    prevalidate_batches,
    resolve_max_sql_bytes,
)
from .manifest import ManifestReader
from .manifest_contract import (
    validate_manifest_changes,
    validate_manifest_metadata,
)


def validate_frozen_plan(path: Path, expected_sha256: str) -> None:
    with ManifestReader(path, expected_sha256) as reader:
        validate_manifest_metadata(reader.metadata)
        validate_source_evidence(reader, reader.metadata)
        validate_manifest_changes(reader.iter_changes(), reader.metadata)
        groups = ordered_groups(reader, APPLY)
        batches, count, _, _ = prevalidate_batches(
            reader,
            groups,
            batch_size=5000,
            max_sql_bytes=resolve_max_sql_bytes(reader.metadata, None),
            direction=APPLY,
        )
        if not batches or count != reader.change_count():
            raise ValueError("frozen plan does not cover every manifest change")
