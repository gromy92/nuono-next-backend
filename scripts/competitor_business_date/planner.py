"""Facade that composes snapshot, event, and rank correction policies."""
from __future__ import annotations

from datetime import datetime
from typing import Any, Iterable, Mapping

from .classification import (
    LIST_V1_RUNTIME_START,
    RANK_LEGACY_MAX_ID,
    SNAPSHOT_LEGACY_MAX_ID,
    WRITER_CUTOVER,
)
from .event_rebuild import EventIdAllocator
from .plan_types import CorrectionPlan, RowChange, count_changes
from .rank_plan import plan_rank_run
from .row_utils import as_int, format_datetime
from .snapshot_plan import chain_key, plan_snapshot_chain


class CorrectionPlanningError(ValueError):
    pass


class CorrectionPlanner:
    def __init__(
        self,
        *,
        actor_user_id: int,
        correction_time: datetime,
        snapshot_clock_overrides: Mapping[int, str] | None = None,
        rank_clock_overrides: Mapping[int, str] | None = None,
        event_contract_overrides: Mapping[int, str] | None = None,
    ):
        if actor_user_id <= 0:
            raise CorrectionPlanningError("actor user ID must be positive")
        if correction_time.microsecond:
            raise CorrectionPlanningError("correction time must have whole-second precision")
        self.actor_user_id = actor_user_id
        self.correction_time = correction_time
        self.snapshot_clock_overrides = dict(snapshot_clock_overrides or {})
        self.rank_clock_overrides = dict(rank_clock_overrides or {})
        self.event_contract_overrides = dict(event_contract_overrides or {})

    def metadata(
        self,
        *,
        run_id: str,
        fence_generation: int,
        source_fingerprint: dict[str, Any],
    ) -> dict[str, Any]:
        if not run_id.strip() or fence_generation <= 0:
            raise CorrectionPlanningError("run ID and positive fence generation are required")
        return {
            "run_id": run_id,
            "algorithm_version": 1,
            "target_schema": "nuonuoai",
            "actor_user_id": self.actor_user_id,
            "correction_time": format_datetime(self.correction_time),
            "fence_generation": fence_generation,
            "writer_cutover": format_datetime(WRITER_CUTOVER),
            "snapshot_legacy_max_id": SNAPSHOT_LEGACY_MAX_ID,
            "rank_legacy_max_id": RANK_LEGACY_MAX_ID,
            "list_v1_runtime_start": format_datetime(LIST_V1_RUNTIME_START),
            "source_fingerprint": source_fingerprint,
            "non_scope_tables": ["operations_competitor_search_result"],
        }

    def plan_snapshot_chain(
        self,
        snapshots: Iterable[dict[str, Any]],
        events: Iterable[dict[str, Any]],
        event_ids: EventIdAllocator,
    ) -> tuple[tuple[RowChange, ...], dict[str, int]]:
        return plan_snapshot_chain(
            snapshots,
            events,
            event_ids,
            actor_user_id=self.actor_user_id,
            correction_time=self.correction_time,
            clock_overrides=self.snapshot_clock_overrides,
            event_contract_overrides=self.event_contract_overrides,
        )

    def plan_rank_run(
        self,
        ranks: Iterable[dict[str, Any]],
        keyword_run: dict[str, Any],
    ) -> tuple[RowChange, ...]:
        return plan_rank_run(
            ranks,
            keyword_run,
            actor_user_id=self.actor_user_id,
            correction_time=self.correction_time,
            clock_overrides=self.rank_clock_overrides,
        )

    def build(
        self,
        *,
        snapshots: Iterable[dict[str, Any]],
        events: Iterable[dict[str, Any]],
        rank_facts: Iterable[dict[str, Any]],
        keyword_runs: Iterable[dict[str, Any]],
        event_sequence: dict[str, Any],
        run_id: str,
        fence_generation: int,
        source_fingerprint: dict[str, Any],
    ) -> CorrectionPlan:
        snapshot_groups = _groups(snapshots, chain_key)
        event_groups = _groups(events, chain_key)
        rank_groups = _groups(
            rank_facts,
            lambda row: str(as_int(row["keyword_run_id"], "rank.keyword_run_id")),
        )
        keyword_by_id = {
            str(as_int(row["id"], "keyword_run.id")): dict(row)
            for row in keyword_runs
        }
        event_ids = EventIdAllocator(dict(event_sequence))
        changes: list[RowChange] = []
        stats = {
            "legacy_snapshot_rows": 0,
            "current_snapshot_rows": 0,
            "snapshot_chain_rows": 0,
            "existing_event_rows": sum(len(rows) for rows in event_groups.values()),
            "legacy_rank_rows": 0,
            "current_rank_rows": 0,
            "keyword_run_rows": len(keyword_by_id),
        }
        for key in sorted(snapshot_groups):
            chain_changes, chain_stats = self.plan_snapshot_chain(
                snapshot_groups[key],
                event_groups.get(key, ()),
                event_ids,
            )
            changes.extend(chain_changes)
            for name, value in chain_stats.items():
                stats[name] += value
        for key in sorted(rank_groups, key=int):
            parent = keyword_by_id.get(key)
            if parent is None:
                raise CorrectionPlanningError(
                    f"keyword run {key} is missing from the frozen scope"
                )
            group_changes = self.plan_rank_run(rank_groups[key], parent)
            changes.extend(group_changes)
            if group_changes:
                stats["legacy_rank_rows"] += len(rank_groups[key])
            else:
                stats["current_rank_rows"] += len(rank_groups[key])
        sequence_change = event_ids.sequence_change(self.correction_time)
        if sequence_change:
            changes.insert(0, sequence_change)
        _assert_unique(changes)
        stats.update(count_changes(changes))
        return CorrectionPlan(
            self.metadata(
                run_id=run_id,
                fence_generation=fence_generation,
                source_fingerprint=source_fingerprint,
            ),
            tuple(changes),
            stats,
        )


def _groups(
    rows: Iterable[dict[str, Any]],
    key_fn: Any,
) -> dict[str, list[dict[str, Any]]]:
    groups: dict[str, list[dict[str, Any]]] = {}
    for source in rows:
        row = dict(source)
        groups.setdefault(str(key_fn(row)), []).append(row)
    return groups


def _assert_unique(changes: Iterable[RowChange]) -> None:
    seen: set[tuple[str, str]] = set()
    for change in changes:
        key = (change.table_name, change.primary_key)
        if key in seen:
            raise CorrectionPlanningError(
                f"row planned more than once: {change.table_name}/{change.primary_key}"
            )
        seen.add(key)
