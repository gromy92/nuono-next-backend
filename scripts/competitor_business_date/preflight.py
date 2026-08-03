"""Read-only preflight and exact schema-fingerprint checks."""
from __future__ import annotations

import hashlib
import json
import os
import re
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable

from .manifest import canonical_json, sha256_file
from .mysql_cli import MysqlCli
from .queries import (
    AMBIGUITY_AUDIT_SQL,
    PREFLIGHT_COUNT_SUMMARY_SQL,
    SERVER_SCHEMA_FINGERPRINT_SQL,
)
from .row_utils import format_datetime
from .schema_contract import is_exact_target_schema
from .schema_query import TABLES
from .secure_files import fsync_directory


class PreflightError(RuntimeError):
    pass


def read_schema_fingerprint(mysql: MysqlCli) -> tuple[list[dict[str, Any]], str]:
    rows = list(mysql.query_json_objects(SERVER_SCHEMA_FINGERPRINT_SQL))
    if not rows:
        raise PreflightError("schema fingerprint returned no rows")
    return rows, schema_fingerprint_digest(rows)


def schema_fingerprint_digest(rows: Iterable[dict[str, Any]]) -> str:
    return hashlib.sha256(canonical_json(list(rows)).encode("utf-8")).hexdigest()


def validate_target_schema(
    rows: Iterable[dict[str, Any]],
    *,
    expected_schema: str = "nuonuoai",
) -> str:
    records = tuple(rows)
    server = next(
        (row for row in records if row.get("record_type") == "server"),
        None,
    )
    if server is None:
        raise PreflightError("schema fingerprint has no server record")
    server_payload = _payload(server)
    if server_payload.get("database") != expected_schema:
        raise PreflightError(
            f"unexpected database schema: {server_payload.get('database')!r}"
        )
    table_records = {
        str(row.get("table_name")): _payload(row)
        for row in records
        if row.get("record_type") == "table"
    }
    if is_exact_target_schema(records):
        return "TARGET"
    legacy_tables = set(TABLES) - {
        "operations_competitor_correction_writer_fence"
    }
    if (
        set(table_records) != legacy_tables
        or any(
            str(payload.get("engine")).upper() != "INNODB"
            or payload.get("table_collation") != "utf8mb4_unicode_ci"
            for payload in table_records.values()
        )
        or any(row.get("record_type") == "trigger" for row in records)
    ):
        return "DRIFT"
    table = "operations_competitor_product_snapshot"
    active = [
        row
        for row in records
        if row.get("record_type") == "column"
        and row.get("table_name") == table
        and row.get("object_name") == "active_fact_date"
    ]
    indexes = [
        row
        for row in records
        if row.get("record_type") == "index"
        and row.get("table_name") == table
    ]
    old = [row for row in indexes if row.get("object_name") == "uk_ops_comp_snapshot_daily"]
    target = sorted(
        (
            row
            for row in indexes
            if row.get("object_name") == "uk_ops_comp_snapshot_active_daily"
        ),
        key=lambda row: int(row["ordinal_position"]),
    )
    target_columns = [
        _payload(row).get("column_name") for row in target
    ]
    target_unique = all(int(_payload(row).get("non_unique", 1)) == 0 for row in target)
    if not active and len(old) == 4 and not target:
        return "LEGACY"
    return "DRIFT"


def server_identity(rows: Iterable[dict[str, Any]]) -> dict[str, Any]:
    server = next(
        (row for row in rows if row.get("record_type") == "server"),
        None,
    )
    if server is None:
        raise PreflightError("schema fingerprint has no server record")
    payload = _payload(server)
    required = (
        "database",
        "server_uuid",
        "hostname",
        "port",
        "version",
        "max_allowed_packet",
    )
    if any(payload.get(key) in {None, ""} for key in required):
        raise PreflightError("schema fingerprint server identity is incomplete")
    return {key: payload[key] for key in required}


def require_server_uuid(
    rows: Iterable[dict[str, Any]],
    expected_uuid: str,
) -> dict[str, Any]:
    if not re.fullmatch(
        r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        expected_uuid,
    ):
        raise PreflightError("expected server UUID is not canonical lowercase UUID")
    identity = server_identity(rows)
    if str(identity["server_uuid"]).lower() != expected_uuid:
        raise PreflightError("connected MySQL server UUID differs from approved target")
    return identity


def run_read_only_preflight(
    mysql: MysqlCli,
    output: Path,
    *,
    now: datetime,
    release_provenance: dict[str, Any],
) -> dict[str, Any]:
    fingerprint, fingerprint_sha = read_schema_fingerprint(mysql)
    metrics = {
        str(row["metric"]): int(row["row_count"])
        for row in mysql.query_json_objects(PREFLIGHT_COUNT_SUMMARY_SQL)
    }
    ambiguity_count = 0
    ambiguity_sample: list[dict[str, Any]] = []
    for row in mysql.query_json_objects(AMBIGUITY_AUDIT_SQL):
        ambiguity_count += 1
        if len(ambiguity_sample) < 100:
            ambiguity_sample.append(row)
    payload = {
        "created_at": format_datetime(now),
        "target_schema": mysql.schema,
        "schema_state": validate_target_schema(fingerprint),
        "schema_fingerprint_sha256": fingerprint_sha,
        "metrics": dict(sorted(metrics.items())),
        "ambiguity_count": ambiguity_count,
        "ambiguity_sample": ambiguity_sample,
        "ambiguity_sample_truncated": ambiguity_count > 100,
        "fingerprint": fingerprint,
        "release_artifact": release_provenance,
        "production_write_authorized": False,
    }
    write_private_json(output, payload)
    payload["output_sha256"] = sha256_file(Path(output))
    return payload


def write_private_json(path: Path, value: Any) -> None:
    target = Path(path)
    if target.is_symlink() or target.exists():
        raise PreflightError(f"output target already exists or is a symlink: {target}")
    target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    flags = os.O_CREAT | os.O_EXCL | os.O_WRONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(target, flags, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2, sort_keys=True)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
    os.chmod(target, 0o400)
    fsync_directory(target.parent)


def _payload(row: dict[str, Any]) -> dict[str, Any]:
    value = row.get("payload_json")
    if not isinstance(value, str):
        raise PreflightError("fingerprint payload is not encoded JSON text")
    decoded = json.loads(value)
    if not isinstance(decoded, dict):
        raise PreflightError("fingerprint payload is not an object")
    return decoded


def _active_column_exact(payload: dict[str, Any]) -> bool:
    expression = "".join(
        str(payload.get("generation_expression") or "")
        .replace("`", "")
        .replace("(", "")
        .replace(")", "")
        .split()
    ).lower()
    expression = (
        expression.replace("_binary", "")
        .replace("b'0'", "0")
        .replace("'\\0'", "0")
        .replace("'0'", "0")
        .replace("0b0", "0")
        .replace("0x00", "0")
    )
    return (
        payload.get("column_type") == "date"
        and str(payload.get("nullable")).upper() == "YES"
        and str(payload.get("extra")).lower() == "virtual generated"
        and expression == "casewhenis_deleted=0thenfact_dateelsenullend"
    )
