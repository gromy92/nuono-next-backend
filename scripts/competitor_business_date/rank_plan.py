"""Plan one legacy keyword-run/rank-fact correction group."""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any, Iterable, Mapping

from .classification import classify_rank_clock
from .plan_types import RowChange
from .policy import LEGACY_CLOCK
from .row_utils import (
    as_bit,
    as_int,
    format_date,
    format_datetime,
    parse_datetime,
    require_columns,
)


RANK_TABLE = "operations_competitor_rank_fact"
KEYWORD_RUN_TABLE = "operations_competitor_keyword_run"


def plan_rank_run(
    rank_facts: Iterable[dict[str, Any]],
    keyword_run: dict[str, Any],
    *,
    actor_user_id: int,
    correction_time: datetime,
    clock_overrides: Mapping[int, str] | None = None,
) -> tuple[RowChange, ...]:
    ranks = tuple(dict(row) for row in rank_facts)
    if not ranks:
        return ()
    run_id = as_int(keyword_run.get("id"), "keyword_run.id")
    require_columns(
        keyword_run,
        (
            "id",
            "search_run_id",
            "keyword_id",
            "captured_at",
            "is_deleted",
            "updated_by",
            "gmt_updated",
        ),
        KEYWORD_RUN_TABLE,
    )
    if as_bit(keyword_run["is_deleted"], "keyword_run.is_deleted"):
        raise ValueError(f"keyword run {run_id} is deleted")
    parent_search_run = as_int(
        keyword_run["search_run_id"], "keyword_run.search_run_id"
    )
    parent_keyword = as_int(keyword_run["keyword_id"], "keyword_run.keyword_id")
    clocks = {
        classify_rank_clock(row, clock_overrides or {}) for row in ranks
    }
    if len(clocks) > 1:
        raise ValueError(f"keyword run {run_id} mixes clock conventions")
    if clocks != {LEGACY_CLOCK}:
        return ()
    captured = parse_datetime(
        keyword_run["captured_at"], "keyword_run.captured_at"
    )
    parent_post = dict(keyword_run)
    parent_post["captured_at"] = format_datetime(captured + timedelta(hours=8))
    parent_post["updated_by"] = actor_user_id
    parent_post["gmt_updated"] = format_datetime(correction_time)
    changes = [
        RowChange(
            "rank_run",
            str(run_id),
            KEYWORD_RUN_TABLE,
            str(run_id),
            "UPDATE",
            dict(keyword_run),
            parent_post,
        )
    ]
    seen: set[int] = set()
    for row in sorted(ranks, key=lambda item: as_int(item["id"], "rank.id")):
        rank_id = as_int(row["id"], "rank.id")
        if rank_id in seen:
            raise ValueError(f"{RANK_TABLE} has duplicate id {rank_id}")
        seen.add(rank_id)
        if as_int(row["keyword_run_id"], "rank.keyword_run_id") != run_id:
            raise ValueError("rank fact does not belong to the supplied keyword run")
        if as_int(row["search_run_id"], "rank.search_run_id") != parent_search_run:
            raise ValueError(
                f"keyword run {run_id} rank search_run_id differs from its parent"
            )
        if as_int(row["keyword_id"], "rank.keyword_id") != parent_keyword:
            raise ValueError(
                f"keyword run {run_id} rank keyword_id differs from its parent"
            )
        fact_time = parse_datetime(row["fact_time"], "rank.fact_time")
        if fact_time != captured:
            raise ValueError(
                f"keyword run {run_id} rank time differs from its parent"
            )
        corrected = fact_time + timedelta(hours=8)
        post = dict(row)
        post["fact_time"] = format_datetime(corrected)
        post["fact_date"] = format_date(corrected.date())
        post["updated_by"] = actor_user_id
        post["gmt_updated"] = format_datetime(correction_time)
        changes.append(
            RowChange(
                "rank_run",
                str(run_id),
                RANK_TABLE,
                str(rank_id),
                "UPDATE",
                dict(row),
                post,
            )
        )
    return tuple(changes)
