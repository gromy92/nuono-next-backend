"""Closed semantic contract for executable correction manifests."""
from __future__ import annotations

import re
from datetime import timedelta
from typing import Any, Iterable

from .bundle_identity import operation_bundle_identity
from .manifest import ManifestChange
from .manifest_metadata_values import (
    MetadataValueError,
    validate_clock_overrides,
    validate_release_artifact,
)
from .row_utils import parse_datetime


class ManifestContractError(RuntimeError):
    pass


_REQUIRED = {
    "schema_version",
    "content_digest",
    "run_id",
    "algorithm_version",
    "target_schema",
    "actor_user_id",
    "correction_time",
    "fence_generation",
    "writer_cutover",
    "snapshot_legacy_max_id",
    "rank_legacy_max_id",
    "list_v1_runtime_start",
    "source_fingerprint",
    "source_snapshot",
    "database_identity",
    "non_scope_tables",
    "operation_bundle",
    "release_artifact",
    "summary",
}
_OPTIONAL = {"clock_overrides"}
_BASE_SUMMARY = {
    "source_snapshot_rows",
    "source_event_rows",
    "source_rank_rows",
    "source_keyword_run_rows",
    "legacy_snapshot_rows",
    "current_snapshot_rows",
    "snapshot_chain_rows",
    "legacy_rank_rows",
    "current_rank_rows",
    "keyword_run_rows",
}


def validate_manifest_metadata(metadata: dict[str, Any]) -> None:
    keys = set(metadata)
    if not _REQUIRED <= keys or keys - _REQUIRED - _OPTIONAL:
        raise ManifestContractError("manifest metadata keys violate the closed contract")
    expected_scalars = {
        "schema_version": 1,
        "algorithm_version": 1,
        "writer_cutover": "2026-07-28 20:00:50",
        "snapshot_legacy_max_id": 358244,
        "rank_legacy_max_id": 1001946,
        "list_v1_runtime_start": "2026-07-29 16:28:40",
        "non_scope_tables": ["operations_competitor_search_result"],
    }
    if any(metadata.get(key) != value for key, value in expected_scalars.items()):
        raise ManifestContractError("manifest policy boundary metadata differs")
    if (
        not isinstance(metadata.get("run_id"), str)
        or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}", metadata["run_id"])
        or not isinstance(metadata.get("target_schema"), str)
        or not re.fullmatch(r"[A-Za-z0-9_]{1,64}", metadata["target_schema"])
        or not _positive_int(metadata.get("actor_user_id"))
        or not _positive_int(metadata.get("fence_generation"))
    ):
        raise ManifestContractError("manifest run/actor/fence metadata is invalid")
    correction_time = parse_datetime(
        metadata.get("correction_time"),
        "manifest.correction_time",
    )
    if correction_time.microsecond:
        raise ManifestContractError("manifest correction time must use whole seconds")
    _validate_source_metadata(metadata)
    operation_bundle = operation_bundle_identity()
    if metadata.get("operation_bundle") != operation_bundle:
        raise ManifestContractError("operation bundle differs from manifest")
    try:
        validate_release_artifact(
            metadata.get("release_artifact"),
            operation_bundle,
        )
        if "clock_overrides" in metadata:
            validate_clock_overrides(metadata["clock_overrides"])
    except MetadataValueError as error:
        raise ManifestContractError(str(error)) from error


def validate_manifest_changes(
    changes: Iterable[ManifestChange],
    metadata: dict[str, Any],
) -> dict[str, int]:
    actor = int(metadata["actor_user_id"])
    correction_time = str(metadata["correction_time"])
    sequence: ManifestChange | None = None
    inserted_count = 0
    inserted_min: int | None = None
    inserted_max: int | None = None
    counts: dict[str, int] = {}
    for change in changes:
        if not isinstance(change.post, dict):
            raise ManifestContractError("manifest change has no POST row")
        key = f"{change.table_name}:{change.action.upper()}"
        counts[key] = counts.get(key, 0) + 1
        if change.table_name.endswith("analysis_id_sequence"):
            if sequence is not None:
                raise ManifestContractError("manifest has multiple sequence changes")
            sequence = change
        if change.action.upper() == "INSERT":
            event_id = int(change.primary_key)
            inserted_count += 1
            inserted_min = event_id if inserted_min is None else min(inserted_min, event_id)
            inserted_max = event_id if inserted_max is None else max(inserted_max, event_id)
            if change.post.get("created_by") != actor:
                raise ManifestContractError("insert actor differs from manifest actor")
        if change.table_name.endswith("analysis_id_sequence"):
            if change.post.get("gmt_updated") != correction_time:
                raise ManifestContractError("sequence audit time differs")
        else:
            if change.post.get("updated_by") != actor:
                raise ManifestContractError("row actor differs from manifest actor")
            if change.post.get("gmt_updated") != correction_time:
                raise ManifestContractError("row audit time differs from correction time")
        _validate_time_shift(change)
    _validate_sequence_coverage(sequence, inserted_count, inserted_min, inserted_max)
    summary = metadata["summary"]
    for key, value in counts.items():
        if summary.get(key) != value:
            raise ManifestContractError(f"summary count differs for {key}")
    summary_change_keys = {key for key in summary if ":" in key}
    if summary_change_keys != set(counts):
        raise ManifestContractError("summary change keys are not exact")
    return dict(sorted(counts.items()))


def _validate_source_metadata(metadata: dict[str, Any]) -> None:
    fingerprint = metadata.get("source_fingerprint")
    if (
        not isinstance(fingerprint, dict)
        or set(fingerprint) != {"sha256", "schema_state"}
        or fingerprint.get("schema_state") != "TARGET"
        or not _sha(fingerprint.get("sha256"))
    ):
        raise ManifestContractError("source fingerprint metadata is invalid")
    source = metadata.get("source_snapshot")
    counts = source.get("counts") if isinstance(source, dict) else None
    if (
        not isinstance(source, dict)
        or set(source) != {"sha256", "counts"}
        or not _sha(source.get("sha256"))
        or not isinstance(counts, dict)
        or set(counts)
        != {
            "schema_fingerprint",
            "event_sequence",
            "snapshot",
            "event",
            "rank",
            "keyword_run",
        }
        or any(not isinstance(value, int) or value < 0 for value in counts.values())
    ):
        raise ManifestContractError("source snapshot metadata is invalid")
    identity = metadata.get("database_identity")
    if (
        not isinstance(identity, dict)
        or set(identity)
        != {"database", "server_uuid", "hostname", "port", "version", "max_allowed_packet"}
        or identity.get("database") != "nuonuoai"
        or not _positive_int(identity.get("port"))
        or not _positive_int(identity.get("max_allowed_packet"))
    ):
        raise ManifestContractError("database identity metadata is invalid")
    summary = metadata.get("summary")
    if (
        not isinstance(summary, dict)
        or not _BASE_SUMMARY <= set(summary)
        or any(not isinstance(value, int) or value < 0 for value in summary.values())
    ):
        raise ManifestContractError("manifest summary metadata is invalid")
    if (
        summary["source_snapshot_rows"] != counts["snapshot"]
        or summary["source_event_rows"] != counts["event"]
        or summary["source_rank_rows"] != counts["rank"]
        or summary["source_keyword_run_rows"] != counts["keyword_run"]
        or summary["snapshot_chain_rows"] != counts["snapshot"]
        or summary["legacy_snapshot_rows"] + summary["current_snapshot_rows"]
        != counts["snapshot"]
        or summary["legacy_rank_rows"] + summary["current_rank_rows"]
        != counts["rank"]
        or summary["keyword_run_rows"] != counts["keyword_run"]
    ):
        raise ManifestContractError("manifest source summary does not reconcile")


def _validate_time_shift(change: ManifestChange) -> None:
    if change.pre is None:
        return
    table = change.table_name
    if table.endswith("product_snapshot"):
        before = parse_datetime(change.pre["captured_at"], "snapshot.pre")
        after = parse_datetime(change.post["captured_at"], "snapshot.post")
        if after not in {before, before + timedelta(hours=8)}:
            raise ManifestContractError("snapshot time shift is not zero/eight hours")
        if change.post["fact_date"] != after.date().isoformat():
            raise ManifestContractError("snapshot fact date differs from corrected time")
    elif table.endswith("rank_fact"):
        before = parse_datetime(change.pre["fact_time"], "rank.pre")
        after = parse_datetime(change.post["fact_time"], "rank.post")
        if after != before + timedelta(hours=8):
            raise ManifestContractError("rank time shift is not eight hours")
        if change.post["fact_date"] != after.date().isoformat():
            raise ManifestContractError("rank fact date differs from corrected time")
    elif table.endswith("keyword_run"):
        before = parse_datetime(change.pre["captured_at"], "run.pre")
        after = parse_datetime(change.post["captured_at"], "run.post")
        if after != before + timedelta(hours=8):
            raise ManifestContractError("keyword-run shift is not eight hours")


def _validate_sequence_coverage(
    sequence: ManifestChange | None,
    count: int,
    minimum: int | None,
    maximum: int | None,
) -> None:
    if not count:
        if sequence is not None:
            raise ManifestContractError("sequence advances without event inserts")
        return
    if sequence is None:
        raise ManifestContractError("event inserts are not covered by a sequence change")
    start = int(sequence.pre["next_id"]) + 1
    end = int(sequence.post["next_id"])
    if count != end - start + 1 or minimum != start or maximum != end:
        raise ManifestContractError("event insert IDs are not exactly sequence-covered")


def _positive_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _sha(value: Any) -> bool:
    return isinstance(value, str) and bool(re.fullmatch(r"[0-9a-f]{64}", value))
