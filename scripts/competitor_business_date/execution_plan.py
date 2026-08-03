"""Prevalidate and pack complete manifest groups into bounded CAS batches."""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from itertools import islice
from typing import Any

from .apply_sql import APPLY, build_batch_sql
from .apply_validation import MAX_BATCH_SIZE, BatchDescriptor, validate_batch
from .manifest import ManifestChange, ManifestReader
from .table_contracts import CHANGE_EVENT, ID_SEQUENCE, KEYWORD_RUN, RANK_FACT, SNAPSHOT

MAX_SAFE_SQL_BYTES = 16 * 1024 * 1024
APPLY_ORDER = ("event_sequence", "snapshot_chain", "rank_run")
ROLLBACK_ORDER = tuple(reversed(APPLY_ORDER))
GROUP_TABLES = {
    "event_sequence": frozenset({ID_SEQUENCE.name}),
    "snapshot_chain": frozenset({SNAPSHOT.name, CHANGE_EVENT.name}),
    "rank_run": frozenset({KEYWORD_RUN.name, RANK_FACT.name}),
}


class ExecutionPlanError(RuntimeError):
    pass


@dataclass(frozen=True)
class ExecutionBatch:
    descriptor: BatchDescriptor
    first_ordinal: int
    last_ordinal: int
    sql_bytes: int

    @property
    def journal_key(self) -> tuple[str, str, int, int]:
        return (
            self.descriptor.group_kind,
            self.descriptor.group_digest,
            self.first_ordinal,
            self.last_ordinal,
        )


def execution_plan_digest(batches: tuple[ExecutionBatch, ...]) -> str:
    payload = [
        [
            batch.descriptor.group_kind,
            list(batch.descriptor.group_keys),
            batch.descriptor.group_digest,
            batch.descriptor.change_count,
            batch.first_ordinal,
            batch.last_ordinal,
            batch.sql_bytes,
        ]
        for batch in batches
    ]
    encoded = json.dumps(
        payload, ensure_ascii=False, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def resolve_max_sql_bytes(
    metadata: dict[str, Any],
    requested: int | None,
) -> int:
    identity = metadata.get("database_identity")
    packet = identity.get("max_allowed_packet") if isinstance(identity, dict) else None
    if isinstance(packet, bool) or not isinstance(packet, int) or packet <= 0:
        raise ExecutionPlanError("manifest database max_allowed_packet is invalid")
    safe = min(packet // 2, MAX_SAFE_SQL_BYTES)
    if safe <= 0:
        raise ExecutionPlanError("manifest database packet limit is unusable")
    if requested is None:
        return safe
    if (
        isinstance(requested, bool)
        or not isinstance(requested, int)
        or requested <= 0
        or requested > safe
    ):
        raise ExecutionPlanError(
            f"max SQL bytes must be positive and at most safe limit {safe}"
        )
    return requested


def ordered_groups(
    reader: ManifestReader,
    direction: str,
) -> tuple[tuple[str, str], ...]:
    by_kind = {kind: [] for kind in APPLY_ORDER}
    for kind, key in reader.group_keys():
        if kind not in by_kind:
            raise ExecutionPlanError(f"unsupported manifest group kind: {kind}")
        by_kind[kind].append(key)
    order = APPLY_ORDER if direction == APPLY else ROLLBACK_ORDER
    result = []
    for kind in order:
        keys = by_kind[kind] if direction == APPLY else reversed(by_kind[kind])
        result.extend((kind, key) for key in keys)
    return tuple(result)


def prevalidate_batches(
    reader: ManifestReader,
    groups: tuple[tuple[str, str], ...],
    *,
    batch_size: int,
    max_sql_bytes: int,
    direction: str,
) -> tuple[tuple[ExecutionBatch, ...], int, int, int]:
    """Validate every row before returning any executable descriptor."""
    validate_batch_size(batch_size)
    batches: list[ExecutionBatch] = []
    pending: list[ManifestChange] = []
    pending_upper_bytes = total = max_group = max_group_sql = 0
    for kind, key in groups:
        group = read_group(reader, kind, key, batch_size)
        validate_batch(group, direction)
        group_bytes = len(build_batch_sql(group, direction=direction).encode("utf-8"))
        if group_bytes > max_sql_bytes:
            raise ExecutionPlanError(
                f"manifest group SQL exceeds byte limit and cannot be split: {kind}/{key}"
            )
        size = len(group)
        total += size
        max_group = max(max_group, size)
        max_group_sql = max(max_group_sql, group_bytes)
        if pending and (
            pending[0].group_kind != kind
            or len(pending) + size > batch_size
            or pending_upper_bytes + group_bytes > max_sql_bytes
        ):
            batches.append(describe_batch(pending, direction, max_sql_bytes))
            pending, pending_upper_bytes = [], 0
        pending.extend(group)
        pending_upper_bytes += group_bytes
        if kind == "event_sequence":
            batches.append(describe_batch(pending, direction, max_sql_bytes))
            pending, pending_upper_bytes = [], 0
    if pending:
        batches.append(describe_batch(pending, direction, max_sql_bytes))
    return tuple(batches), total, max_group, max_group_sql


def describe_batch(
    changes: list[ManifestChange] | tuple[ManifestChange, ...],
    direction: str,
    max_sql_bytes: int,
) -> ExecutionBatch:
    rows = tuple(changes)
    descriptor = validate_batch(rows, direction)
    sql_bytes = len(build_batch_sql(rows, direction=direction).encode("utf-8"))
    if sql_bytes > max_sql_bytes:
        raise ExecutionPlanError("packed CAS batch exceeds max SQL byte limit")
    ordinals = [change.ordinal for change in rows]
    return ExecutionBatch(descriptor, min(ordinals), max(ordinals), sql_bytes)


def load_batch(
    reader: ManifestReader,
    batch: ExecutionBatch,
    *,
    batch_size: int,
    max_sql_bytes: int,
    direction: str,
) -> tuple[ManifestChange, ...]:
    rows: list[ManifestChange] = []
    for key in batch.descriptor.group_keys:
        rows.extend(read_group(reader, batch.descriptor.group_kind, key, batch_size))
    if len(rows) > batch_size:
        raise ExecutionPlanError("prevalidated batch now exceeds batch size")
    if describe_batch(rows, direction, max_sql_bytes) != batch:
        raise ExecutionPlanError(
            "manifest batch differs from its prevalidated descriptor"
        )
    return tuple(rows)


def read_group(
    reader: ManifestReader,
    kind: str,
    key: str,
    batch_size: int,
) -> tuple[ManifestChange, ...]:
    rows = tuple(islice(
        reader.iter_changes(group_kind=kind, group_key=key), batch_size + 1
    ))
    if not rows:
        raise ExecutionPlanError(f"manifest group disappeared: {kind}/{key}")
    if len(rows) > batch_size:
        raise ExecutionPlanError(
            f"manifest group exceeds batch size and cannot be split: {kind}/{key}"
        )
    if any(change.table_name not in GROUP_TABLES[kind] for change in rows):
        raise ExecutionPlanError(f"manifest group contains an invalid table: {kind}/{key}")
    return rows


def validate_batch_size(batch_size: int) -> None:
    if (
        isinstance(batch_size, bool)
        or not isinstance(batch_size, int)
        or not 1 <= batch_size <= MAX_BATCH_SIZE
    ):
        raise ExecutionPlanError(
            f"batch size must be between 1 and {MAX_BATCH_SIZE}"
        )
