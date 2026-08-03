"""Frozen-source query routing and disk staging for correction planning."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Callable

from .consistent_read import ConsistentDatasetReader
from .queries import (
    CHANGE_EVENT_CHAIN_SQL,
    EVENT_SEQUENCE_SQL,
    KEYWORD_RUN_ROWS_SQL,
    RANK_FACT_ROWS_SQL,
    SERVER_SCHEMA_FINGERPRINT_SQL,
    SNAPSHOT_CHAIN_SQL,
)
from .row_utils import as_int
from .snapshot_plan import chain_key
from .source_stage import SourceStage


class PlanSourceError(RuntimeError):
    pass


def source_queries() -> tuple[tuple[str, str], ...]:
    return (
        ("schema_fingerprint", SERVER_SCHEMA_FINGERPRINT_SQL),
        ("event_sequence", EVENT_SEQUENCE_SQL),
        ("snapshot", SNAPSHOT_CHAIN_SQL),
        ("event", CHANGE_EVENT_CHAIN_SQL),
        ("rank", RANK_FACT_ROWS_SQL),
        ("keyword_run", KEYWORD_RUN_ROWS_SQL),
    )


def validate_work_dir(path: Path) -> Path:
    candidate = Path(path)
    if candidate.is_symlink() or not candidate.is_dir():
        raise PlanSourceError("work directory must be an existing real directory")
    info = os.stat(candidate)
    if info.st_mode & 0o077:
        raise PlanSourceError("work directory must deny group/other access")
    stats = os.statvfs(candidate)
    if stats.f_bavail * stats.f_frsize < 5 * 1024 * 1024 * 1024:
        raise PlanSourceError("work directory requires at least 5 GiB free")
    return candidate.resolve()


def stage_schema_fingerprint(
    stage: SourceStage,
    rows: list[dict[str, Any]],
) -> int:
    def row_key(row: dict[str, Any]) -> str:
        return "|".join(
            (
                str(row.get("record_type", "")),
                str(row.get("table_name", "")),
                str(row.get("object_name", "")),
                f"{as_int(row.get('ordinal_position', 0), 'ordinal_position'):08d}",
            )
        )

    return stage.ingest(
        "schema_fingerprint",
        rows,
        group_key=lambda row: str(row.get("record_type", "")),
        row_key=row_key,
    )


def stage_event_sequence(
    stage: SourceStage,
    rows: list[dict[str, Any]],
) -> int:
    return stage.ingest(
        "event_sequence",
        rows,
        group_key=lambda row: str(row["sequence_name"]),
        row_key=lambda row: str(row["sequence_name"]),
    )


def stage_business_source(
    reader: ConsistentDatasetReader,
    stage: SourceStage,
) -> dict[str, int]:
    numeric_key: Callable[[dict[str, Any]], str] = lambda row: (
        f"{as_int(row['id'], 'row.id'):020d}"
    )
    return {
        "snapshot": stage.ingest(
            "snapshot",
            reader.read("snapshot"),
            group_key=chain_key,
            row_key=numeric_key,
        ),
        "event": stage.ingest(
            "event",
            reader.read("event"),
            group_key=chain_key,
            row_key=numeric_key,
        ),
        "rank": stage.ingest(
            "rank",
            reader.read("rank"),
            group_key=lambda row: str(row["keyword_run_id"]),
            row_key=numeric_key,
        ),
        "keyword_run": stage.ingest(
            "keyword_run",
            reader.read("keyword_run"),
            group_key=lambda row: str(row["id"]),
            row_key=numeric_key,
        ),
    }
