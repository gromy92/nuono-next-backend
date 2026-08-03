"""Rebuild active field-change events for corrected snapshot chains."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any, Iterable, Mapping

from .classification import event_contract_for_snapshot
from .plan_types import RowChange
from .policy import ExpectedEvent, PlannedSnapshot, build_expected_events
from .row_utils import (
    as_bit,
    as_int,
    format_date,
    format_datetime,
    json_scalar,
    require_columns,
)


EVENT_TABLE = "operations_competitor_product_change_event"
SEQUENCE_TABLE = "operations_competitor_analysis_id_sequence"
EVENT_SEQUENCE = "operations_competitor_product_change_event"


@dataclass
class EventIdAllocator:
    pre: dict[str, Any]

    def __post_init__(self) -> None:
        require_columns(
            self.pre,
            ("sequence_name", "next_id", "gmt_create", "gmt_updated"),
            SEQUENCE_TABLE,
        )
        if self.pre["sequence_name"] != EVENT_SEQUENCE:
            raise ValueError("unexpected competitor event sequence row")
        self.initial = as_int(self.pre["next_id"], "event_sequence.next_id")
        self.current = self.initial

    def require_not_behind(self, event_id: int) -> None:
        if self.initial < event_id:
            raise ValueError("event sequence is behind an existing event ID")

    def allocate(self) -> int:
        self.current += 1
        return self.current

    def sequence_change(self, correction_time: datetime) -> RowChange | None:
        if self.current == self.initial:
            return None
        post = dict(self.pre)
        post["next_id"] = self.current
        post["gmt_updated"] = format_datetime(correction_time)
        return RowChange(
            "event_sequence",
            EVENT_SEQUENCE,
            SEQUENCE_TABLE,
            EVENT_SEQUENCE,
            "UPDATE",
            dict(self.pre),
            post,
        )


def rebuild_event_changes(
    plans: Iterable[PlannedSnapshot],
    snapshot_rows: dict[int, dict[str, Any]],
    snapshot_post_rows: dict[int, dict[str, Any]],
    existing_events: Iterable[dict[str, Any]],
    event_ids: EventIdAllocator,
    *,
    actor_user_id: int,
    correction_time: datetime,
    contract_overrides: Mapping[int, str] | None = None,
) -> tuple[RowChange, ...]:
    planned = tuple(plans)
    contracts = {
        item.snapshot.id: event_contract_for_snapshot(
            snapshot_rows[item.snapshot.id],
            contract_overrides,
        )
        for item in planned
    }
    expected = build_expected_events(planned, contracts)
    event_rows = tuple(existing_events)
    for row in event_rows:
        require_columns(
            row,
            ("id", "snapshot_id", "field_key", "is_deleted"),
            EVENT_TABLE,
        )

    active_by_key: dict[tuple[int, str], list[dict[str, Any]]] = {}
    for row in event_rows:
        if not as_bit(row["is_deleted"], "event.is_deleted"):
            key = (as_int(row["snapshot_id"], "event.snapshot_id"), row["field_key"])
            active_by_key.setdefault(key, []).append(row)
    for rows in active_by_key.values():
        rows.sort(key=lambda row: as_int(row["id"], "event.id"))

    max_existing_id = max(
        (as_int(row["id"], "event.id") for row in event_rows),
        default=0,
    )
    event_ids.require_not_behind(max_existing_id)

    used_ids: set[int] = set()
    changes: list[RowChange] = []
    for desired in expected:
        key = (desired.snapshot_id, desired.field_key)
        candidates = active_by_key.get(key, [])
        selected = candidates[-1] if candidates else None
        if selected is None:
            next_event_id = event_ids.allocate()
            post = _new_event_row(
                next_event_id,
                desired,
                snapshot_post_rows[desired.snapshot_id],
                actor_user_id,
                correction_time,
            )
            changes.append(
                _change_for_event(post, "INSERT", None)
            )
            continue
        selected_id = as_int(selected["id"], "event.id")
        used_ids.add(selected_id)
        post = _updated_event_row(
            selected,
            desired,
            snapshot_post_rows[desired.snapshot_id],
            actor_user_id,
            correction_time,
        )
        if post != selected:
            changes.append(
                _change_for_event(post, "UPDATE", selected)
            )

    for row in event_rows:
        event_id = as_int(row["id"], "event.id")
        if as_bit(row["is_deleted"], "event.is_deleted") or event_id in used_ids:
            continue
        post = dict(row)
        post["is_deleted"] = 1
        post["updated_by"] = actor_user_id
        post["gmt_updated"] = format_datetime(correction_time)
        changes.append(_change_for_event(post, "UPDATE", row))

    return tuple(changes)


def _event_values(
    desired: ExpectedEvent,
    snapshot: dict[str, Any],
) -> dict[str, Any]:
    return {
        "snapshot_id": desired.snapshot_id,
        "previous_snapshot_id": desired.previous_snapshot_id,
        "owner_user_id": snapshot["owner_user_id"],
        "watch_product_id": snapshot["watch_product_id"],
        "competitor_product_id": snapshot.get("competitor_product_id"),
        "subject_type": snapshot["subject_type"],
        "site_code": snapshot["site_code"],
        "noon_product_code": snapshot["noon_product_code"],
        "fact_date": format_date(desired.fact_date),
        "field_key": desired.field_key,
        "field_label": desired.field_label,
        "change_type": desired.change_type,
        "old_value_json": json_scalar(desired.old_value),
        "new_value_json": json_scalar(desired.new_value),
        "severity": desired.severity,
        "is_deleted": 0,
    }


def _new_event_row(
    event_id: int,
    desired: ExpectedEvent,
    snapshot: dict[str, Any],
    actor: int,
    correction_time: datetime,
) -> dict[str, Any]:
    row = {"id": event_id, **_event_values(desired, snapshot)}
    row.update(
        {
            "created_by": actor,
            "updated_by": actor,
            "gmt_create": snapshot["gmt_create"],
            "gmt_updated": format_datetime(correction_time),
        }
    )
    return row


def _updated_event_row(
    pre: dict[str, Any],
    desired: ExpectedEvent,
    snapshot: dict[str, Any],
    actor: int,
    correction_time: datetime,
) -> dict[str, Any]:
    post = dict(pre)
    post.update(_event_values(desired, snapshot))
    post["updated_by"] = actor
    post["gmt_updated"] = format_datetime(correction_time)
    return post


def _change_for_event(
    post: dict[str, Any],
    action: str,
    pre: dict[str, Any] | None,
) -> RowChange:
    key = (
        f"{post['watch_product_id']}|{post['subject_type']}|"
        f"{post['noon_product_code']}"
    )
    return RowChange(
        "snapshot_chain",
        key,
        EVENT_TABLE,
        str(post["id"]),
        action,
        None if pre is None else dict(pre),
        post,
    )
