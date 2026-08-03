"""Fail-closed legacy/current clock and event-contract classification."""
from __future__ import annotations

from datetime import datetime
from typing import Any, Mapping

from .policy import CURRENT_CLOCK, LEGACY_CLOCK
from .row_utils import as_int, parse_date, parse_datetime, require_columns


WRITER_CUTOVER = datetime(2026, 7, 28, 20, 0, 50)
SNAPSHOT_LEGACY_MAX_ID = 358244
RANK_LEGACY_MAX_ID = 1001946
LIST_V1_RUNTIME_START = datetime(2026, 7, 29, 16, 28, 40)


class AmbiguousClockError(ValueError):
    pass


class AmbiguousEventContractError(ValueError):
    pass


def classify_snapshot_clock(
    row: dict[str, Any],
    overrides: Mapping[int, str] | None = None,
) -> str:
    require_columns(
        row,
        ("id", "captured_at", "fact_date", "gmt_create", "gmt_updated"),
        "operations_competitor_product_snapshot",
    )
    row_id = as_int(row["id"], "snapshot.id")
    override = (overrides or {}).get(row_id)
    if override is not None:
        return _validate_override(override, "snapshot", row_id)
    captured = parse_datetime(row["captured_at"], "snapshot.captured_at")
    created = parse_datetime(row["gmt_create"], "snapshot.gmt_create")
    updated = parse_datetime(row["gmt_updated"], "snapshot.gmt_updated")
    fact_date = parse_date(row["fact_date"], "snapshot.fact_date")

    if row_id <= SNAPSHOT_LEGACY_MAX_ID:
        if updated < WRITER_CUTOVER:
            return LEGACY_CLOCK
        raise AmbiguousClockError(
            "bounded snapshot clock is ambiguous after writer cutover; an audited "
            f"override is required for id={row_id}"
        )
    if (
        row_id > SNAPSHOT_LEGACY_MAX_ID
        and created >= WRITER_CUTOVER
        and captured >= WRITER_CUTOVER
        and fact_date == captured.date()
    ):
        return CURRENT_CLOCK
    raise AmbiguousClockError(
        "snapshot clock is ambiguous; freeze an audited override for "
        f"id={row_id}, captured_at={captured}, gmt_updated={updated}"
    )


def classify_rank_clock(
    row: dict[str, Any],
    overrides: Mapping[int, str] | None = None,
) -> str:
    require_columns(
        row,
        ("id", "fact_time", "fact_date", "gmt_create", "gmt_updated"),
        "operations_competitor_rank_fact",
    )
    row_id = as_int(row["id"], "rank.id")
    override = (overrides or {}).get(row_id)
    if override is not None:
        return _validate_override(override, "rank", row_id)
    fact_time = parse_datetime(row["fact_time"], "rank.fact_time")
    created = parse_datetime(row["gmt_create"], "rank.gmt_create")
    updated = parse_datetime(row["gmt_updated"], "rank.gmt_updated")
    fact_date = parse_date(row["fact_date"], "rank.fact_date")

    if row_id <= RANK_LEGACY_MAX_ID:
        if updated < WRITER_CUTOVER:
            return LEGACY_CLOCK
        raise AmbiguousClockError(
            "bounded rank clock is ambiguous after writer cutover; an audited "
            f"override is required for id={row_id}"
        )
    if (
        row_id > RANK_LEGACY_MAX_ID
        and created >= WRITER_CUTOVER
        and fact_time >= WRITER_CUTOVER
        and fact_date == fact_time.date()
    ):
        return CURRENT_CLOCK
    raise AmbiguousClockError(
        "rank clock is ambiguous; freeze an audited override for "
        f"id={row_id}, fact_time={fact_time}, gmt_updated={updated}"
    )


def event_contract_for_snapshot(
    row: dict[str, Any],
    overrides: Mapping[int, str] | None = None,
) -> str:
    require_columns(
        row,
        ("id", "gmt_create", "gmt_updated"),
        "operations_competitor_product_snapshot",
    )
    row_id = as_int(row["id"], "snapshot.id")
    override = (overrides or {}).get(row_id)
    if override is not None:
        if override not in {"legacy", "list_v1"}:
            raise AmbiguousEventContractError(
                f"invalid event contract override for snapshot id={row_id}"
            )
        return override
    created = parse_datetime(row["gmt_create"], "snapshot.gmt_create")
    updated = parse_datetime(row["gmt_updated"], "snapshot.gmt_updated")
    if updated < LIST_V1_RUNTIME_START:
        return "legacy"
    if created >= LIST_V1_RUNTIME_START:
        return "list_v1"
    raise AmbiguousEventContractError(
        "snapshot spans the list-v1 runtime boundary; an audited event-contract "
        f"override is required for id={row_id}"
    )


def _validate_override(value: str, kind: str, row_id: int) -> str:
    if value not in {LEGACY_CLOCK, CURRENT_CLOCK}:
        raise AmbiguousClockError(
            f"invalid {kind} clock override for id={row_id}: {value!r}"
        )
    return value
