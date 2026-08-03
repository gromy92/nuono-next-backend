"""Generate fail-closed MySQL 8 CAS transactions from manifest changes."""
from __future__ import annotations
from typing import Iterable
from .apply_validation import (
    BatchDescriptor, BatchValidationError, MAX_BATCH_SIZE, validate_batch,
)
from .manifest import ManifestChange
from .table_contracts import (
    ID_SEQUENCE, SNAPSHOT, TABLE_CONTRACTS, TableContract, mysql_text_literal,
    typed_literal,
)
APPLY = "apply"
ROLLBACK = "rollback"
class ApplySqlError(RuntimeError):
    pass
def build_batch_sql(changes: Iterable[ManifestChange], *, direction: str = APPLY) -> str:
    """Return a guarded PRE/POST transaction for one manifest group."""
    batch = tuple(changes)
    try:
        descriptor = validate_batch(batch, direction)
    except BatchValidationError as error:
        raise ApplySqlError(str(error)) from error
    ordered = tuple(sorted(batch, key=lambda change: change.ordinal))
    active = tuple(
        change
        for change in ordered
        if not (direction == ROLLBACK and change.table_name == ID_SEQUENCE.name)
    )
    tables = tuple(
        TABLE_CONTRACTS[name]
        for name in TABLE_CONTRACTS
        if any(change.table_name == name for change in active)
    )
    lines = [
        "SET SESSION innodb_lock_wait_timeout = 5;",
        "SET SESSION lock_wait_timeout = 5;",
        "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;",
        "START TRANSACTION;",
        "CREATE TEMPORARY TABLE `_cbd_cas_guard` (",
        "  `must_equal_one` TINYINT NOT NULL CHECK (`must_equal_one` = 1)",
        ") ENGINE=InnoDB;",
    ]
    for contract in tables:
        table_changes = tuple(c for c in active if c.table_name == contract.name)
        lines.extend(_temporary_tables(contract, table_changes))
        lines.extend(_state_guards(contract, direction))
    if SNAPSHOT in tables:
        lines.extend(_snapshot_stage(direction))
    mutation_order = active if direction == APPLY else tuple(reversed(active))
    for change in mutation_order:
        lines.extend(_mutation(change, direction))
    for contract in tables:
        lines.extend(_final_guard(contract, direction))
    sentinel = _success_sentinel(descriptor, direction)
    lines.extend(
        [
            "COMMIT;",
            f"SELECT {mysql_text_literal(sentinel)} "
            "AS `correction_batch_result`;",
        ]
    )
    return "\n".join(lines) + "\n"


def expected_success_sentinel(
    changes: Iterable[ManifestChange],
    *,
    direction: str = APPLY,
) -> str:
    try:
        descriptor = validate_batch(tuple(changes), direction)
    except BatchValidationError as error:
        raise ApplySqlError(str(error)) from error
    return _success_sentinel(descriptor, direction)


def _success_sentinel(descriptor: BatchDescriptor, direction: str) -> str:
    return (
        f"COMPETITOR_BUSINESS_DATE_BATCH_OK|direction={direction}"
        f"|group_kind={descriptor.group_kind}"
        f"|group_count={len(descriptor.group_keys)}"
        f"|group_digest={descriptor.group_digest}"
        f"|change_count={descriptor.change_count}"
    )
def _temporary_tables(
    contract: TableContract, changes: tuple[ManifestChange, ...],
) -> list[str]:
    pre_name, post_name = _temp_names(contract)
    definitions = [
        "  `manifest_ordinal` BIGINT NOT NULL,",
        "  `manifest_action` VARCHAR(8) NOT NULL,",
        *[
            f"  `{column.name}` {column.sql_type}{'' if column.nullable else ' NOT NULL'},"
            for column in contract.row_columns
        ],
        "  PRIMARY KEY (`manifest_ordinal`)",
    ]
    result = [
        f"CREATE TEMPORARY TABLE `{pre_name}` (",
        *definitions,
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;",
        f"CREATE TEMPORARY TABLE `{post_name}` (",
        *definitions,
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;",
    ]
    pre_rows = [(change, change.pre) for change in changes if change.pre is not None]
    post_rows = [(change, change.post) for change in changes]
    result.extend(_load_rows(pre_name, contract, pre_rows))
    result.extend(_load_rows(post_name, contract, post_rows))
    return result
def _load_rows(
    temp_name: str, contract: TableContract,
    rows: list[tuple[ManifestChange, dict[str, Any]]],
) -> list[str]:
    if not rows:
        return []
    columns = ["manifest_ordinal", "manifest_action"] + [
        column.name for column in contract.row_columns
    ]
    values = []
    for change, row in rows:
        literals = [str(change.ordinal), mysql_text_literal(change.action.upper())]
        literals.extend(
            typed_literal(row[column.name], column) for column in contract.row_columns
        )
        values.append("  (" + ", ".join(literals) + ")")
    return [
        f"INSERT INTO `{temp_name}` ({_quoted_columns(columns)}) VALUES",
        ",\n".join(values) + ";",
    ]
def _state_guards(contract: TableContract, direction: str) -> list[str]:
    pre, post = _temp_names(contract)
    target = f"`{contract.name}`"
    pk = contract.primary_key
    update_expected = (pre, post) if direction == APPLY else (post, pre)
    source, destination = update_expected
    update_bad = (
        f"t.`{pk}` IS NULL OR "
        f"(NOT ({_row_match('t', 's', contract)}) "
        f"AND NOT ({_row_match('t', 'd', contract)}))"
    )
    insert_bad = f"t.`{pk}` IS NOT NULL AND NOT ({_row_match('t', 'p', contract)})"
    return [
        "INSERT INTO `_cbd_cas_guard` (`must_equal_one`)",
        "SELECT IF(EXISTS(",
        f"  SELECT 1 FROM `{source}` s",
        f"  JOIN `{destination}` d ON d.`manifest_ordinal` = s.`manifest_ordinal`",
        f"  LEFT JOIN {target} t ON t.`{pk}` = s.`{pk}`",
        f"  WHERE s.`manifest_action` = {mysql_text_literal('UPDATE')} AND ({update_bad})",
        "), 0, 1);",
        "INSERT INTO `_cbd_cas_guard` (`must_equal_one`)",
        "SELECT IF(EXISTS(",
        f"  SELECT 1 FROM `{post}` p",
        f"  LEFT JOIN {target} t ON t.`{pk}` = p.`{pk}`",
        f"  WHERE p.`manifest_action` = {mysql_text_literal('INSERT')} AND ({insert_bad})",
        "), 0, 1);",
    ]
def _mutation(change: ManifestChange, direction: str) -> list[str]:
    contract = TABLE_CONTRACTS[change.table_name]
    pre, post = _temp_names(contract)
    pk = contract.primary_key
    action = change.action.upper()
    if action == "INSERT" and direction == APPLY:
        columns = [column.name for column in contract.row_columns]
        return [
            f"INSERT INTO `{contract.name}` ({_quoted_columns(columns)})",
            f"SELECT {_selected_columns('p', columns)} FROM `{post}` p",
            f"LEFT JOIN `{contract.name}` t ON t.`{pk}` = p.`{pk}`",
            f"WHERE p.`manifest_ordinal` = {change.ordinal} AND t.`{pk}` IS NULL;",
        ]
    if action == "INSERT":
        return [
            f"DELETE t FROM `{contract.name}` t JOIN `{post}` p ON t.`{pk}` = p.`{pk}`",
            f"WHERE p.`manifest_ordinal` = {change.ordinal}",
            f"  AND {_row_match('t', 'p', contract)};",
        ]
    source, destination = (pre, post) if direction == APPLY else (post, pre)
    source_match = _mutation_source_match(contract)
    assignments = ", ".join(
        f"t.`{column.name}` = d.`{column.name}`"
        for column in contract.row_columns
        if column.name != pk
    )
    return [
        f"UPDATE `{contract.name}` t",
        f"JOIN `{source}` s ON t.`{pk}` = s.`{pk}`",
        f"JOIN `{destination}` d ON d.`manifest_ordinal` = s.`manifest_ordinal`",
        f"SET {assignments}",
        f"WHERE s.`manifest_ordinal` = {change.ordinal}",
        f"  AND ({source_match})",
        f"  AND NOT ({_row_match('t', 'd', contract)});",
    ]

def _snapshot_stage(direction: str) -> list[str]:
    pre, post = _temp_names(SNAPSHOT)
    source = pre if direction == APPLY else post
    return [
        f"UPDATE `{SNAPSHOT.name}` t JOIN `{source}` s ON t.`id` = s.`id`",
        "SET t.`is_deleted` = b'1', t.`gmt_updated` = t.`gmt_updated`",
        f"WHERE s.`manifest_action` = {mysql_text_literal('UPDATE')}",
        "  AND s.`is_deleted` = b'0'",
        f"  AND {_row_match('t', 's', SNAPSHOT)};",
    ]

def _mutation_source_match(contract: TableContract) -> str:
    exact = _row_match("t", "s", contract)
    if contract is not SNAPSHOT:
        return exact
    staged = (
        "s.`is_deleted` = b'0' AND t.`is_deleted` = b'1' AND "
        + _row_match("t", "s", contract, exclude="is_deleted")
    )
    return f"({exact}) OR ({staged})"

def _final_guard(contract: TableContract, direction: str) -> list[str]:
    pre, post = _temp_names(contract)
    desired = post if direction == APPLY else pre
    pk = contract.primary_key
    update_bad = f"t.`{pk}` IS NULL OR NOT ({_row_match('t', 'd', contract)})"
    insert_bad = (
        f"t.`{pk}` IS NULL OR NOT ({_row_match('t', 'p', contract)})"
        if direction == APPLY else f"t.`{pk}` IS NOT NULL"
    )
    return [
        "INSERT INTO `_cbd_cas_guard` (`must_equal_one`)",
        "SELECT IF(EXISTS(",
        f"  SELECT 1 FROM `{desired}` d",
        f"  LEFT JOIN `{contract.name}` t ON t.`{pk}` = d.`{pk}`",
        f"  WHERE d.`manifest_action` = {mysql_text_literal('UPDATE')} AND ({update_bad})",
        "), 0, 1);",
        "INSERT INTO `_cbd_cas_guard` (`must_equal_one`)",
        "SELECT IF(EXISTS(",
        f"  SELECT 1 FROM `{post}` p",
        f"  LEFT JOIN `{contract.name}` t ON t.`{pk}` = p.`{pk}`",
        f"  WHERE p.`manifest_action` = {mysql_text_literal('INSERT')} AND ({insert_bad})",
        "), 0, 1);",
    ]


def _row_match(
    left: str, right: str, contract: TableContract, *, exclude: str | None = None,
) -> str:
    comparisons = []
    for column in contract.row_columns:
        if column.name == exclude:
            continue
        lhs, rhs = f"{left}.`{column.name}`", f"{right}.`{column.name}`"
        if column.kind in {"text", "json"}:
            lhs, rhs = f"CAST({lhs} AS BINARY)", f"CAST({rhs} AS BINARY)"
        comparisons.append(f"({lhs} <=> {rhs})")
    return " AND ".join(comparisons)


def _temp_names(contract: TableContract) -> tuple[str, str]:
    return f"_cbd_pre_{contract.temp_token}", f"_cbd_post_{contract.temp_token}"


def _quoted_columns(columns: list[str]) -> str:
    return ", ".join(f"`{column}`" for column in columns)


def _selected_columns(alias: str, columns: list[str]) -> str:
    return ", ".join(f"{alias}.`{column}`" for column in columns)
