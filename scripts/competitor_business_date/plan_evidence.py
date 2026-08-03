"""Reader-facing scope mappings preserved beside correction PRE/POST rows."""
from __future__ import annotations

from typing import Any, Iterable

from .event_rebuild import EVENT_TABLE, SEQUENCE_TABLE
from .plan_types import RowChange
from .rank_plan import KEYWORD_RUN_TABLE, RANK_TABLE
from .row_utils import as_bit, as_int
from .snapshot_plan import SNAPSHOT_TABLE


def snapshot_resolution(
    snapshots: Iterable[dict[str, Any]],
    events: Iterable[dict[str, Any]],
    changes: Iterable[RowChange],
) -> dict[str, Any]:
    source_snapshots = tuple(dict(row) for row in snapshots)
    source_events = tuple(dict(row) for row in events)
    snapshot_rows = {
        as_int(row["id"], "snapshot.id"): row for row in source_snapshots
    }
    event_rows = {as_int(row["id"], "event.id"): row for row in source_events}
    inserted_event_ids: list[int] = []
    for change in changes:
        if change.table_name == SNAPSHOT_TABLE:
            snapshot_rows[int(change.primary_key)] = dict(change.post)
        elif change.table_name == EVENT_TABLE:
            event_id = int(change.primary_key)
            event_rows[event_id] = dict(change.post)
            if change.action.upper() == "INSERT":
                inserted_event_ids.append(event_id)
    corrected = [
        {
            "id": row_id,
            "captured_at": row["captured_at"],
            "fact_date": row["fact_date"],
            "is_deleted": as_bit(row["is_deleted"], "snapshot.is_deleted"),
            "role": (
                "REDUNDANT"
                if as_bit(row["is_deleted"], "snapshot.is_deleted")
                else "CANONICAL"
            ),
        }
        for row_id, row in sorted(snapshot_rows.items())
    ]
    active_events = [
        {
            "id": event_id,
            "snapshot_id": as_int(row["snapshot_id"], "event.snapshot_id"),
            "previous_snapshot_id": row.get("previous_snapshot_id"),
            "field_key": str(row["field_key"]),
            "fact_date": str(row["fact_date"]),
        }
        for event_id, row in sorted(event_rows.items())
        if not as_bit(row["is_deleted"], "event.is_deleted")
    ]
    return {
        "candidate_snapshot_ids": sorted(snapshot_rows),
        "corrected_snapshots": corrected,
        "source_event_ids": sorted(
            as_int(row["id"], "event.id") for row in source_events
        ),
        "inserted_event_ids": sorted(inserted_event_ids),
        "desired_active_events": active_events,
    }


def rank_resolution(
    ranks: Iterable[dict[str, Any]],
    keyword_run: dict[str, Any],
    changes: Iterable[RowChange],
) -> dict[str, Any]:
    rows = tuple(ranks)
    parent_changes = [
        change for change in changes if change.table_name == KEYWORD_RUN_TABLE
    ]
    rank_changes = [
        change for change in changes if change.table_name == RANK_TABLE
    ]
    return {
        "keyword_run_id": as_int(keyword_run["id"], "keyword_run.id"),
        "search_run_id": as_int(
            keyword_run["search_run_id"], "keyword_run.search_run_id"
        ),
        "keyword_id": as_int(keyword_run["keyword_id"], "keyword_run.keyword_id"),
        "candidate_rank_ids": sorted(
            as_int(row["id"], "rank.id") for row in rows
        ),
        "parent_change_count": len(parent_changes),
        "rank_change_ids": sorted(int(change.primary_key) for change in rank_changes),
    }


def sequence_resolution(
    source: dict[str, Any],
    change: RowChange | None,
) -> dict[str, Any]:
    return {
        "sequence_name": source["sequence_name"],
        "source_next_id": as_int(source["next_id"], "sequence.next_id"),
        "source_max_event_id": as_int(
            source["max_event_id"], "sequence.max_event_id"
        ),
        "planned_next_id": (
            as_int(change.post["next_id"], "sequence.post.next_id")
            if change
            else as_int(source["next_id"], "sequence.next_id")
        ),
    }
