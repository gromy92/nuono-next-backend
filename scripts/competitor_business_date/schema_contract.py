"""Compare a MySQL fingerprint with the correction's golden schema contract."""
from __future__ import annotations

import json
import re
from collections import defaultdict
from typing import Any, Iterable

from .persistent_fence import FENCE_TABLE
from .schema_expectations import DEFAULTS, INDEXES, TABLE_COMMENT, columns


def is_exact_target_schema(rows: Iterable[dict[str, Any]]) -> bool:
    records = tuple(rows)
    return (
        _tables_exact(records)
        and _columns_exact(records)
        and _indexes_exact(records)
        and _constraints_exact(records)
        and not any(row.get("record_type") == "trigger" for row in records)
    )


def _tables_exact(rows: tuple[dict[str, Any], ...]) -> bool:
    actual = {
        str(row.get("table_name")): _payload(row)
        for row in rows
        if row.get("record_type") == "table"
    }
    if set(actual) != set(INDEXES):
        return False
    return all(
        str(payload.get("engine")).upper() == "INNODB"
        and payload.get("table_collation") == "utf8mb4_unicode_ci"
        and payload.get("table_comment", "") == TABLE_COMMENT.get(table, "")
        for table, payload in actual.items()
    )


def _columns_exact(rows: tuple[dict[str, Any], ...]) -> bool:
    actual: dict[str, list[tuple[str, dict[str, Any]]]] = defaultdict(list)
    for row in rows:
        if row.get("record_type") == "column":
            actual[str(row["table_name"])].append(
                (
                    int(row["ordinal_position"]),
                    str(row["object_name"]),
                    _payload(row),
                )
            )
    expected = columns()
    if set(actual) != set(expected):
        return False
    for table, definitions in expected.items():
        observed = sorted(actual[table])
        if [name for _, name, _ in observed] != [item[0] for item in definitions]:
            return False
        for (ordinal, name, payload), (_, sql_type, nullable, kind) in zip(
            observed, definitions
        ):
            if (
                ordinal != definitions.index((name, sql_type, nullable, kind)) + 1
                or
                str(payload.get("column_type")).lower() != sql_type
                or str(payload.get("nullable")).upper()
                != ("YES" if nullable else "NO")
                or not _column_encoding_exact(payload, kind)
                or not _column_default_exact(table, name, payload)
                or not _column_extra_exact(table, name, payload)
            ):
                return False
    return True


def _indexes_exact(rows: tuple[dict[str, Any], ...]) -> bool:
    actual: dict[str, dict[str, list[dict[str, Any]]]] = defaultdict(
        lambda: defaultdict(list)
    )
    for row in rows:
        if row.get("record_type") == "index":
            actual[str(row["table_name"])][str(row["object_name"])].append(row)
    if set(actual) != set(INDEXES):
        return False
    for table, expected in INDEXES.items():
        if set(actual[table]) != set(expected):
            return False
        for name, (non_unique, expected_columns) in expected.items():
            observed = sorted(
                actual[table][name], key=lambda row: int(row["ordinal_position"])
            )
            if tuple(_payload(row).get("column_name") for row in observed) != expected_columns:
                return False
            if any(
                bool(int(_payload(row).get("non_unique", 1))) != non_unique
                or str(_payload(row).get("index_type")).upper() != "BTREE"
                or str(_payload(row).get("visible")).upper() != "YES"
                or _payload(row).get("sub_part") is not None
                or _payload(row).get("expression") is not None
                or str(_payload(row).get("collation")).upper() != "A"
                for row in observed
            ):
                return False
    return True


def _constraints_exact(rows: tuple[dict[str, Any], ...]) -> bool:
    constraints: dict[str, set[tuple[str, str]]] = defaultdict(set)
    checks: dict[str, dict[str, str]] = defaultdict(dict)
    key_usage: dict[str, list[tuple[str, int, str]]] = defaultdict(list)
    for row in rows:
        table = str(row.get("table_name"))
        if row.get("record_type") == "constraint":
            payload = _payload(row)
            constraints[table].add(
                (str(row["object_name"]), str(payload.get("constraint_type")))
            )
        elif row.get("record_type") == "check":
            checks[table][str(row["object_name"])] = str(
                _payload(row).get("check_clause")
            )
        elif row.get("record_type") == "key_usage":
            payload = _payload(row)
            if any(payload.get(key) is not None for key in (
                "referenced_table_schema",
                "referenced_table_name",
                "referenced_column_name",
            )):
                return False
            key_usage[table].append(
                (
                    str(row["object_name"]),
                    int(row["ordinal_position"]),
                    str(payload.get("column_name")),
                )
            )
    expected_constraints = {
        table: {
            (name, "PRIMARY KEY" if name == "PRIMARY" else "UNIQUE")
            for name, (non_unique, _) in indexes.items()
            if not non_unique
        }
        for table, indexes in INDEXES.items()
    }
    expected_constraints[FENCE_TABLE] |= {
        ("chk_ops_comp_cwf_name", "CHECK"),
        ("chk_ops_comp_cwf_status", "CHECK"),
        ("chk_ops_comp_cwf_active_audit", "CHECK"),
    }
    if dict(constraints) != expected_constraints:
        return False
    if set(checks) != {FENCE_TABLE} or not _fence_checks_exact(checks[FENCE_TABLE]):
        return False
    expected_usage = {
        table: sorted(
            (name, ordinal, column)
            for name, (non_unique, names) in indexes.items()
            if not non_unique
            for ordinal, column in enumerate(names, 1)
        )
        for table, indexes in INDEXES.items()
    }
    return {
        table: sorted(values) for table, values in key_usage.items()
    } == expected_usage


def _column_encoding_exact(payload: dict[str, Any], kind: str) -> bool:
    if kind == "text":
        return (
            payload.get("charset") == "utf8mb4"
            and payload.get("collation") == "utf8mb4_unicode_ci"
        )
    return payload.get("charset") is None and payload.get("collation") is None


def _column_default_exact(
    table: str,
    name: str,
    payload: dict[str, Any],
) -> bool:
    value = payload.get("default")
    if name in {"gmt_create", "gmt_updated"}:
        return str(value).upper().startswith("CURRENT_TIMESTAMP")
    normalized = _normalize_default(value)
    return normalized == DEFAULTS.get((table, name))


def _column_extra_exact(table: str, name: str, payload: dict[str, Any]) -> bool:
    extra = " ".join(str(payload.get("extra") or "").upper().split())
    expression = str(payload.get("generation_expression") or "")
    if table.endswith("product_snapshot") and name == "active_fact_date":
        return (
            extra == "VIRTUAL GENERATED"
            and _normalize_expression(expression)
            == "casewhenis_deleted=0thenfact_dateelsenullend"
        )
    if name == "gmt_updated":
        return "ON UPDATE CURRENT_TIMESTAMP" in extra and not expression
    return not expression and (
        not extra or (name == "gmt_create" and extra == "DEFAULT_GENERATED")
    )


def _fence_checks_exact(checks: dict[str, str]) -> bool:
    if set(checks) != {
        "chk_ops_comp_cwf_name",
        "chk_ops_comp_cwf_status",
        "chk_ops_comp_cwf_active_audit",
    }:
        return False
    normalized = {name: _normalize_expression(value) for name, value in checks.items()}
    expected_audit = (
        "fence_status=openandgeneration=0andoperation_run_idisnull"
        "andactivated_byisnullandactivated_atisnullandreopened_byisnull"
        "andreopened_atisnullorfence_status=activeandgeneration>0"
        "andoperation_run_idisnotnullandactivated_byisnotnull"
        "andactivated_atisnotnullandreopened_byisnullandreopened_atisnull"
        "orfence_status=openandgeneration>0andoperation_run_idisnotnull"
        "andactivated_byisnotnullandactivated_atisnotnull"
        "andreopened_byisnotnullandreopened_atisnotnull"
    )
    return (
        normalized["chk_ops_comp_cwf_name"]
        == "fence_name=historical_business_date_correction"
        and normalized["chk_ops_comp_cwf_status"]
        == "fence_statusinopen,active"
        and normalized["chk_ops_comp_cwf_active_audit"] == expected_audit
    )


def _normalize_default(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip().lower()
    if normalized in {"b'0'", "'\\0'", "\\0", "0b0", "0x00", "'0'", "0"}:
        return "0"
    return normalized.strip("'").upper()


def _normalize_expression(value: str) -> str:
    normalized = (
        re.sub(r"[`()'\s]", "", value)
        .lower()
        .replace("_binary", "")
        .replace("_utf8mb4", "")
        .replace("\\0", "0")
    )
    return normalized.replace("0x00", "0").replace("0b0", "0").replace("b0", "0")


def _payload(row: dict[str, Any]) -> dict[str, Any]:
    value = row.get("payload_json")
    if not isinstance(value, str):
        return {}
    decoded = json.loads(value)
    return decoded if isinstance(decoded, dict) else {}
