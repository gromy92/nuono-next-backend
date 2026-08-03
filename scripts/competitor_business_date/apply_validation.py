"""Closed-contract validation and identity for CAS batches."""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from typing import Any

from .manifest import ManifestChange, row_digest
from .table_contracts import (
    CHANGE_EVENT,
    ContractValueError,
    ID_SEQUENCE,
    KEYWORD_RUN,
    RANK_FACT,
    SNAPSHOT,
    TABLE_CONTRACTS,
    TableContract,
    typed_literal,
)


MAX_BATCH_SIZE = 5000
EVENT_SEQUENCE = "operations_competitor_product_change_event"
_GROUP_TABLES = {
    "event_sequence": frozenset({ID_SEQUENCE.name}),
    "snapshot_chain": frozenset({SNAPSHOT.name, CHANGE_EVENT.name}),
    "rank_run": frozenset({RANK_FACT.name, KEYWORD_RUN.name}),
}
_UPDATE_COLUMNS = {
    SNAPSHOT.name: frozenset(
        {"captured_at", "fact_date", "is_deleted", "updated_by", "gmt_updated"}
    ),
    CHANGE_EVENT.name: frozenset(
        {
            "previous_snapshot_id",
            "fact_date",
            "field_label",
            "change_type",
            "old_value_json",
            "new_value_json",
            "severity",
            "is_deleted",
            "updated_by",
            "gmt_updated",
        }
    ),
    RANK_FACT.name: frozenset(
        {"fact_time", "fact_date", "updated_by", "gmt_updated"}
    ),
    KEYWORD_RUN.name: frozenset({"captured_at", "updated_by", "gmt_updated"}),
    ID_SEQUENCE.name: frozenset({"next_id", "gmt_updated"}),
}


class BatchValidationError(ValueError):
    pass


@dataclass(frozen=True)
class BatchDescriptor:
    group_kind: str
    group_keys: tuple[str, ...]
    group_digest: str
    change_count: int


def validate_batch(
    batch: tuple[ManifestChange, ...],
    direction: str,
) -> BatchDescriptor:
    if direction not in {"apply", "rollback"}:
        raise BatchValidationError("direction must be apply or rollback")
    if not 1 <= len(batch) <= MAX_BATCH_SIZE:
        raise BatchValidationError(
            f"batch must contain between 1 and {MAX_BATCH_SIZE} changes"
        )
    kinds = {change.group_kind for change in batch}
    if len(kinds) != 1:
        raise BatchValidationError(
            "one CAS batch may contain only one manifest group kind"
        )
    groups = tuple(dict.fromkeys(change.group_key for change in batch))
    ordinals = [change.ordinal for change in batch]
    if any(not isinstance(value, int) or value <= 0 for value in ordinals):
        raise BatchValidationError("manifest ordinals must be positive integers")
    if len(ordinals) != len(set(ordinals)):
        raise BatchValidationError("manifest ordinals must be unique within a batch")
    targets = [(change.table_name, change.primary_key) for change in batch]
    if len(targets) != len(set(targets)):
        raise BatchValidationError("a batch must not change the same target row twice")
    for change in batch:
        contract = TABLE_CONTRACTS.get(change.table_name)
        if contract is None:
            raise BatchValidationError(
                f"table is not in the correction allowlist: {change.table_name}"
            )
        _validate_change(change, contract)
    _validate_complete_groups(batch, next(iter(kinds)), groups)
    digest_input = json.dumps(
        [[next(iter(kinds)), key] for key in groups],
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    return BatchDescriptor(
        next(iter(kinds)),
        groups,
        hashlib.sha256(digest_input).hexdigest(),
        len(batch),
    )


def _validate_complete_groups(
    batch: tuple[ManifestChange, ...],
    kind: str,
    group_keys: tuple[str, ...],
) -> None:
    for group_key in group_keys:
        group = tuple(change for change in batch if change.group_key == group_key)
        table_counts: dict[str, int] = {}
        for change in group:
            table_counts[change.table_name] = table_counts.get(change.table_name, 0) + 1
        if kind == "event_sequence":
            valid = len(group) == 1 and table_counts == {ID_SEQUENCE.name: 1}
        elif kind == "snapshot_chain":
            valid = table_counts.get(SNAPSHOT.name, 0) >= 1
        elif kind == "rank_run":
            valid = (
                table_counts.get(KEYWORD_RUN.name) == 1
                and table_counts.get(RANK_FACT.name, 0) >= 1
            )
        else:
            valid = False
        if not valid:
            raise BatchValidationError(
                f"manifest group is incomplete: {kind}/{group_key}"
            )


def _validate_change(change: ManifestChange, contract: TableContract) -> None:
    action = change.action.upper()
    if action not in {"UPDATE", "INSERT"}:
        raise BatchValidationError(f"unsupported manifest action: {change.action}")
    if action == "UPDATE" and (change.pre is None or change.post is None):
        raise BatchValidationError("UPDATE requires complete PRE and POST rows")
    if action == "INSERT" and (change.pre is not None or change.post is None):
        raise BatchValidationError("INSERT requires absent PRE and a complete POST row")
    if contract is ID_SEQUENCE:
        if action != "UPDATE":
            raise BatchValidationError("analysis id sequence only permits UPDATE")
        if change.primary_key != EVENT_SEQUENCE:
            raise BatchValidationError("only the competitor change-event sequence is allowed")
    elif contract is not CHANGE_EVENT and action != "UPDATE":
        raise BatchValidationError(f"{contract.name} only permits UPDATE")
    for label, row, digest in (
        ("PRE", change.pre, change.pre_digest),
        ("POST", change.post, change.post_digest),
    ):
        if row_digest(row) != digest:
            raise BatchValidationError(
                f"{label} digest mismatch at ordinal {change.ordinal}"
            )
        if row is not None:
            _validate_row(row, contract, label)
            if str(row[contract.primary_key]) != change.primary_key:
                raise BatchValidationError(
                    f"{label} primary key does not match manifest key"
                )
    if action == "UPDATE" and (
        change.pre[contract.primary_key] != change.post[contract.primary_key]
    ):
        raise BatchValidationError("UPDATE must not change the primary key")
    if contract is ID_SEQUENCE and change.post["next_id"] < change.pre["next_id"]:
        raise BatchValidationError("analysis id sequence may only move forward")
    _validate_scope_and_diff(change, contract)


def _validate_row(
    row: dict[str, Any],
    contract: TableContract,
    label: str,
) -> None:
    actual = frozenset(row)
    if actual != contract.row_column_names:
        missing = sorted(contract.row_column_names - actual)
        extra = sorted(actual - contract.row_column_names)
        raise BatchValidationError(
            f"{label} row columns differ; missing={missing}, extra={extra}"
        )
    for column in contract.row_columns:
        try:
            typed_literal(row[column.name], column)
        except ContractValueError as error:
            raise BatchValidationError(str(error)) from error


def _validate_scope_and_diff(
    change: ManifestChange,
    contract: TableContract,
) -> None:
    allowed_tables = _GROUP_TABLES.get(change.group_kind)
    if allowed_tables is None or contract.name not in allowed_tables:
        raise BatchValidationError(
            f"group kind {change.group_kind!r} cannot mutate {contract.name}"
        )
    expected_key = _expected_group_key(change.post, contract)
    if change.group_key != expected_key:
        raise BatchValidationError(
            f"group key mismatch for {contract.name}/{change.primary_key}"
        )
    if change.action.upper() == "INSERT":
        return
    changed = {
        column
        for column in contract.row_column_names
        if change.pre[column] != change.post[column]
    }
    if not changed:
        raise BatchValidationError("UPDATE manifest row must not be a no-op")
    unexpected = changed - _UPDATE_COLUMNS[contract.name]
    if unexpected:
        raise BatchValidationError(
            f"UPDATE changes forbidden columns: {sorted(unexpected)}"
        )


def _expected_group_key(row: dict[str, Any], contract: TableContract) -> str:
    if contract in {SNAPSHOT, CHANGE_EVENT}:
        return (
            f"{row['watch_product_id']}|{row['subject_type']}|"
            f"{row['noon_product_code']}"
        )
    if contract is RANK_FACT:
        return str(row["keyword_run_id"])
    if contract is KEYWORD_RUN:
        return str(row["id"])
    return EVENT_SEQUENCE
