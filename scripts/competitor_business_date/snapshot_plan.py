"""Plan one affected competitor snapshot business-key chain."""
from __future__ import annotations

from datetime import datetime
from typing import Any, Iterable, Mapping

from .classification import classify_snapshot_clock
from .event_rebuild import EventIdAllocator, rebuild_event_changes
from .plan_types import RowChange
from .policy import LEGACY_CLOCK, Snapshot, plan_daily_canonicalization
from .row_utils import (
    as_bit,
    as_int,
    format_date,
    format_datetime,
    parse_datetime,
    require_columns,
)


SNAPSHOT_TABLE = "operations_competitor_product_snapshot"


def plan_snapshot_chain(
    snapshots: Iterable[dict[str, Any]],
    events: Iterable[dict[str, Any]],
    event_ids: EventIdAllocator,
    *,
    actor_user_id: int,
    correction_time: datetime,
    clock_overrides: Mapping[int, str] | None = None,
    event_contract_overrides: Mapping[int, str] | None = None,
) -> tuple[tuple[RowChange, ...], dict[str, int]]:
    rows = _unique_rows(snapshots)
    models = tuple(
        _to_model(row, clock_overrides or {}) for row in rows.values()
    )
    if not models:
        return (), {
            "legacy_snapshot_rows": 0,
            "current_snapshot_rows": 0,
            "snapshot_chain_rows": 0,
        }
    keys = {model.business_key for model in models}
    if len(keys) != 1:
        raise ValueError("snapshot planner accepts exactly one business-key chain")
    legacy_count = sum(model.clock == LEGACY_CLOCK for model in models)
    if legacy_count == 0:
        return (), {
            "legacy_snapshot_rows": 0,
            "current_snapshot_rows": len(models),
            "snapshot_chain_rows": len(models),
        }

    plans = plan_daily_canonicalization(models)
    post_rows, snapshot_changes = _snapshot_changes(
        plans,
        rows,
        actor_user_id,
        correction_time,
    )
    event_changes = rebuild_event_changes(
        plans,
        rows,
        post_rows,
        tuple(events),
        event_ids,
        actor_user_id=actor_user_id,
        correction_time=correction_time,
        contract_overrides=event_contract_overrides,
    )
    return tuple(snapshot_changes) + event_changes, {
        "legacy_snapshot_rows": legacy_count,
        "current_snapshot_rows": len(models) - legacy_count,
        "snapshot_chain_rows": len(models),
    }


def _to_model(
    row: dict[str, Any],
    overrides: Mapping[int, str],
) -> Snapshot:
    require_columns(
        row,
        (
            "id",
            "watch_product_id",
            "subject_type",
            "noon_product_code",
            "captured_at",
            "is_deleted",
            "updated_by",
            "gmt_updated",
        ),
        SNAPSHOT_TABLE,
    )
    return Snapshot(
        id=as_int(row["id"], "snapshot.id"),
        watch_product_id=as_int(
            row["watch_product_id"], "snapshot.watch_product_id"
        ),
        subject_type=str(row["subject_type"]),
        noon_product_code=str(row["noon_product_code"]),
        captured_at=parse_datetime(row["captured_at"], "snapshot.captured_at"),
        clock=classify_snapshot_clock(row, overrides),
        is_deleted=as_bit(row["is_deleted"], "snapshot.is_deleted"),
        values=dict(row),
    )


def _snapshot_changes(
    plans: Iterable[Any],
    rows: dict[int, dict[str, Any]],
    actor_user_id: int,
    correction_time: datetime,
) -> tuple[dict[int, dict[str, Any]], list[RowChange]]:
    post_rows: dict[int, dict[str, Any]] = {}
    changes: list[RowChange] = []
    for plan in plans:
        pre = rows[plan.snapshot.id]
        post = dict(pre)
        post["captured_at"] = format_datetime(plan.effective_captured_at)
        post["fact_date"] = format_date(plan.fact_date)
        post["is_deleted"] = int(plan.is_deleted)
        post_rows[plan.snapshot.id] = post
        if not any(
            post[column] != pre[column]
            for column in ("captured_at", "fact_date", "is_deleted")
        ):
            continue
        post["updated_by"] = actor_user_id
        post["gmt_updated"] = format_datetime(correction_time)
        changes.append(
            RowChange(
                "snapshot_chain",
                chain_key(pre),
                SNAPSHOT_TABLE,
                str(plan.snapshot.id),
                "UPDATE",
                dict(pre),
                post,
            )
        )
    return post_rows, changes


def chain_key(row: dict[str, Any]) -> str:
    return (
        f"{row['watch_product_id']}|{row['subject_type']}|"
        f"{row['noon_product_code']}"
    )


def _unique_rows(
    rows: Iterable[dict[str, Any]],
) -> dict[int, dict[str, Any]]:
    result: dict[int, dict[str, Any]] = {}
    for source in rows:
        row = dict(source)
        if "id" not in row:
            raise ValueError(f"{SNAPSHOT_TABLE} row is missing id")
        row_id = as_int(row["id"], "snapshot.id")
        if row_id in result:
            raise ValueError(f"{SNAPSHOT_TABLE} has duplicate id {row_id}")
        result[row_id] = row
    return result
