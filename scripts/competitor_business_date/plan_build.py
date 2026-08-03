"""Build manifest changes and mappings from one frozen disk-backed source."""
from __future__ import annotations

from .classification import (
    AmbiguousClockError,
    AmbiguousEventContractError,
    classify_rank_clock,
    classify_snapshot_clock,
    event_contract_for_snapshot,
)
from .event_rebuild import EventIdAllocator
from .manifest import ManifestWriter
from .overrides import ClockOverrides
from .plan_evidence import (
    rank_resolution,
    sequence_resolution,
    snapshot_resolution,
)
from .plan_types import RowChange, count_changes
from .planner import CorrectionPlanner
from .source_stage import SourceStage


class PlanBuildError(RuntimeError):
    pass


def copy_source_evidence(stage: SourceStage, writer: ManifestWriter) -> None:
    for kind, group_key, row_key, row in stage.iter_rows():
        writer.add_source_row(
            kind=kind,
            group_key=group_key,
            row_key=row_key,
            row=row,
        )


def write_plan(
    stage: SourceStage,
    writer: ManifestWriter,
    planner: CorrectionPlanner,
    event_ids: EventIdAllocator,
    source_counts: dict[str, int],
) -> dict[str, int]:
    change_counts: dict[str, int] = {}
    legacy_snapshots = 0
    current_snapshots = 0
    chain_rows = 0
    for key in stage.group_keys("snapshot"):
        snapshots = tuple(stage.rows("snapshot", key))
        events = tuple(stage.rows("event", key))
        changes, stats = planner.plan_snapshot_chain(
            snapshots,
            events,
            event_ids,
        )
        _append_changes(writer, changes)
        writer.add_resolution(
            kind="snapshot_chain",
            group_key=key,
            resolution=snapshot_resolution(snapshots, events, changes),
        )
        _merge_counts(change_counts, count_changes(changes))
        legacy_snapshots += stats["legacy_snapshot_rows"]
        current_snapshots += stats["current_snapshot_rows"]
        chain_rows += stats["snapshot_chain_rows"]
    legacy_ranks = 0
    current_ranks = 0
    for key in stage.group_keys("rank"):
        rank_rows = tuple(stage.rows("rank", key))
        keyword_run = stage.one("keyword_run", key)
        changes = planner.plan_rank_run(rank_rows, keyword_run)
        _append_changes(writer, changes)
        writer.add_resolution(
            kind="rank_run",
            group_key=key,
            resolution=rank_resolution(rank_rows, keyword_run, changes),
        )
        _merge_counts(change_counts, count_changes(changes))
        if changes:
            legacy_ranks += len(rank_rows)
        else:
            current_ranks += len(rank_rows)
    sequence_change = event_ids.sequence_change(planner.correction_time)
    if sequence_change:
        _append_changes(writer, (sequence_change,))
        _merge_counts(change_counts, count_changes((sequence_change,)))
    sequence = stage.one(
        "event_sequence",
        "operations_competitor_product_change_event",
    )
    writer.add_resolution(
        kind="event_sequence",
        group_key="operations_competitor_product_change_event",
        resolution=sequence_resolution(sequence, sequence_change),
    )
    summary = {
        "source_snapshot_rows": source_counts["snapshot"],
        "source_event_rows": source_counts["event"],
        "source_rank_rows": source_counts["rank"],
        "source_keyword_run_rows": source_counts["keyword_run"],
        "legacy_snapshot_rows": legacy_snapshots,
        "current_snapshot_rows": current_snapshots,
        "snapshot_chain_rows": chain_rows,
        "legacy_rank_rows": legacy_ranks,
        "current_rank_rows": current_ranks,
        "keyword_run_rows": source_counts["keyword_run"],
        **change_counts,
    }
    if chain_rows != source_counts["snapshot"]:
        raise PlanBuildError("snapshot classifications do not reconcile to source")
    if legacy_ranks + current_ranks != source_counts["rank"]:
        raise PlanBuildError("rank classifications do not reconcile to source")
    if legacy_snapshots == 0 or legacy_ranks == 0:
        raise PlanBuildError("frozen scope unexpectedly lacks legacy rows")
    return summary


def validate_override_coverage(
    stage: SourceStage,
    overrides: ClockOverrides | None,
) -> None:
    if not overrides:
        return
    for kind, stage_kind, values, classifier, ambiguous_error in (
        (
            "snapshot",
            "snapshot",
            overrides.snapshot,
            classify_snapshot_clock,
            AmbiguousClockError,
        ),
        (
            "rank",
            "rank",
            overrides.rank,
            classify_rank_clock,
            AmbiguousClockError,
        ),
        (
            "event_contract",
            "snapshot",
            overrides.event_contract,
            event_contract_for_snapshot,
            AmbiguousEventContractError,
        ),
    ):
        missing = [
            row_id
            for row_id in values
            if not stage.has_row(stage_kind, f"{row_id:020d}")
        ]
        if missing:
            raise PlanBuildError(
                f"{kind} clock overrides are outside frozen scope: {missing[:10]}"
            )
        for row_id in values:
            row = stage.row_by_key(stage_kind, f"{row_id:020d}")
            try:
                classifier(row, {})
            except ambiguous_error:
                continue
            raise PlanBuildError(
                f"{kind} override {row_id} targets an unambiguous row"
            )


def _append_changes(
    writer: ManifestWriter,
    changes: tuple[RowChange, ...],
) -> None:
    for change in changes:
        writer.add_change(
            group_kind=change.group_kind,
            group_key=change.group_key,
            table_name=change.table_name,
            primary_key=change.primary_key,
            action=change.action,
            pre=change.pre,
            post=change.post,
        )


def _merge_counts(target: dict[str, int], addition: dict[str, int]) -> None:
    for key, value in addition.items():
        target[key] = target.get(key, 0) + value
