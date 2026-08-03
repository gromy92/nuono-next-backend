"""Closed contract for the scoped source backup embedded in a manifest."""
from __future__ import annotations

from collections import defaultdict
from typing import Any

from .manifest import ManifestReader


SOURCE_KINDS = frozenset(
    {
        "schema_fingerprint",
        "event_sequence",
        "snapshot",
        "event",
        "rank",
        "keyword_run",
    }
)


class EvidenceContractError(RuntimeError):
    pass


def validate_source_evidence(
    reader: ManifestReader,
    metadata: dict[str, Any],
) -> dict[str, int]:
    declared = metadata.get("source_snapshot")
    if not isinstance(declared, dict):
        raise EvidenceContractError("manifest source snapshot metadata is missing")
    raw_counts = reader.source_counts()
    if set(raw_counts) - SOURCE_KINDS:
        raise EvidenceContractError("scoped source evidence contains unknown kinds")
    actual_counts = {
        kind: raw_counts.get(kind, 0) for kind in sorted(SOURCE_KINDS)
    }
    if declared.get("counts") != actual_counts:
        raise EvidenceContractError("scoped source counts differ from metadata")
    if declared.get("sha256") != reader.source_content_digest():
        raise EvidenceContractError("scoped source digest differs from metadata")

    groups: dict[str, set[str]] = defaultdict(set)
    for source in reader.iter_source_rows():
        if source.kind not in SOURCE_KINDS:
            raise EvidenceContractError("scoped source contains an unknown kind")
        groups[source.kind].add(source.group_key)
    resolutions: dict[str, set[str]] = defaultdict(set)
    for resolution in reader.iter_resolutions():
        if resolution.kind not in {"snapshot_chain", "rank_run", "event_sequence"}:
            raise EvidenceContractError("planning resolution kind is invalid")
        resolutions[resolution.kind].add(resolution.group_key)
        _validate_resolution_shape(resolution.kind, resolution.resolution)
    expected = {
        "snapshot_chain": groups["snapshot"],
        "rank_run": groups["rank"],
        "event_sequence": groups["event_sequence"],
    }
    if {key: resolutions[key] for key in expected} != expected:
        raise EvidenceContractError(
            "planning resolutions do not cover every frozen source group"
        )
    if set(resolutions) != set(expected):
        raise EvidenceContractError("planning resolution coverage has extra kinds")
    return actual_counts


def _validate_resolution_shape(kind: str, value: dict[str, Any]) -> None:
    required = {
        "snapshot_chain": {
            "candidate_snapshot_ids",
            "corrected_snapshots",
            "source_event_ids",
            "inserted_event_ids",
            "desired_active_events",
        },
        "rank_run": {
            "keyword_run_id",
            "search_run_id",
            "keyword_id",
            "candidate_rank_ids",
            "parent_change_count",
            "rank_change_ids",
        },
        "event_sequence": {
            "sequence_name",
            "source_next_id",
            "source_max_event_id",
            "planned_next_id",
        },
    }[kind]
    if set(value) != required:
        raise EvidenceContractError(f"{kind} resolution shape is invalid")
