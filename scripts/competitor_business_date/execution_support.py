"""Durable execution journal and read-only exact-state SQL."""
from __future__ import annotations
import json
import os
import stat
from pathlib import Path
from typing import Any, Iterable
from .apply_sql import APPLY, MAX_BATCH_SIZE, ROLLBACK
from .manifest import ManifestChange, canonical_json
from .table_contracts import (
    ID_SEQUENCE,
    TABLE_CONTRACTS,
    TableContract,
    mysql_text_literal,
    typed_literal,
)
class ExecutionSupportError(RuntimeError):
    pass
def verify_sentinel(direction: str, change_count: int) -> str:
    return (
        "COMPETITOR_BUSINESS_DATE_VERIFY_OK"
        f"|direction={direction}|change_count={change_count}"
    )
def build_verify_sql(
    changes: Iterable[ManifestChange],
    *,
    direction: str,
) -> str:
    """Build a read-only, exact-row verification script."""
    batch = tuple(changes)
    if direction not in {APPLY, ROLLBACK}:
        raise ExecutionSupportError("verification direction must be apply or rollback")
    if not 1 <= len(batch) <= MAX_BATCH_SIZE:
        raise ExecutionSupportError(
            f"verification must contain between 1 and {MAX_BATCH_SIZE} changes"
        )
    tables = tuple(
        contract
        for name, contract in TABLE_CONTRACTS.items()
        if any(change.table_name == name for change in batch)
    )
    if not tables or any(change.table_name not in TABLE_CONTRACTS for change in batch):
        raise ExecutionSupportError("verification contains a non-allowlisted table")
    lines = [
        "SET SESSION innodb_lock_wait_timeout = 5;",
        "SET SESSION lock_wait_timeout = 5;",
        "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;",
    ]
    for contract in tables:
        lines.extend(_expected_table(contract, batch, direction))
    lines.append("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;")
    conflict_queries = [_conflict_query(contract) for contract in tables]
    ok = verify_sentinel(direction, len(batch))
    conflict = ok.replace("_OK|", "_CONFLICT|")
    lines.extend(
        [
            "SELECT IF(EXISTS(",
            "\nUNION ALL\n".join(conflict_queries),
            f"), {mysql_text_literal(conflict)}, {mysql_text_literal(ok)});",
            "COMMIT;",
        ]
    )
    return "\n".join(lines) + "\n"
def _expected_table(
    contract: TableContract,
    changes: tuple[ManifestChange, ...],
    direction: str,
) -> list[str]:
    rows = [change for change in changes if change.table_name == contract.name]
    columns = contract.row_columns
    definitions = [
        "  `manifest_ordinal` BIGINT NOT NULL,",
        "  `expect_present` TINYINT NOT NULL,",
        *[
            f"  `{column.name}` {column.sql_type}"
            f"{'' if column.nullable else ' NOT NULL'},"
            for column in columns
        ],
        "  PRIMARY KEY (`manifest_ordinal`)",
    ]
    values = []
    for change in rows:
        expected, present = _expected_row(change, direction)
        if expected is None:
            raise ExecutionSupportError("verification expected row is absent")
        _validate_expected(change, expected, contract)
        literals = [str(change.ordinal), "1" if present else "0"]
        literals.extend(typed_literal(expected[column.name], column) for column in columns)
        values.append("  (" + ", ".join(literals) + ")")
    names = ["manifest_ordinal", "expect_present", *[c.name for c in columns]]
    temp = _temp_name(contract)
    return [
        f"CREATE TEMPORARY TABLE `{temp}` (",
        *definitions,
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;",
        f"INSERT INTO `{temp}` ({_quoted(names)}) VALUES",
        ",\n".join(values) + ";",
    ]
def _expected_row(change: ManifestChange, direction: str
                  ) -> tuple[dict[str, Any] | None, bool]:
    if direction == APPLY or change.table_name == ID_SEQUENCE.name:
        return change.post, True
    if change.action.upper() == "INSERT":
        return change.post, False
    return change.pre, True
def _validate_expected(
    change: ManifestChange,
    row: dict[str, Any],
    contract: TableContract,
) -> None:
    if frozenset(row) != contract.row_column_names:
        raise ExecutionSupportError(
            f"verification row columns differ at ordinal {change.ordinal}"
        )
    if str(row[contract.primary_key]) != change.primary_key:
        raise ExecutionSupportError(
            f"verification primary key differs at ordinal {change.ordinal}"
        )
def _conflict_query(contract: TableContract) -> str:
    expected = _temp_name(contract)
    primary_key = contract.primary_key
    mismatch = (
        f"(e.`expect_present` = 1 AND "
        f"(t.`{primary_key}` IS NULL OR NOT ({_row_match(contract)}))) "
        f"OR (e.`expect_present` = 0 AND t.`{primary_key}` IS NOT NULL)"
    )
    return (
        f"  SELECT 1 FROM `{expected}` e "
        f"LEFT JOIN `{contract.name}` t "
        f"ON t.`{primary_key}` = e.`{primary_key}` WHERE {mismatch}"
    )
def _row_match(contract: TableContract) -> str:
    comparisons = []
    for column in contract.row_columns:
        left, right = f"t.`{column.name}`", f"e.`{column.name}`"
        if column.kind in {"text", "json"}:
            left, right = f"CAST({left} AS BINARY)", f"CAST({right} AS BINARY)"
        comparisons.append(f"({left} <=> {right})")
    return " AND ".join(comparisons)
def _temp_name(contract: TableContract) -> str:
    return f"_cbd_verify_{contract.temp_token}"
def _quoted(columns: list[str]) -> str:
    return ", ".join(f"`{column}`" for column in columns)
class ExecutionJournal:
    """Append-only, fsync'd journal bound to one manifest and fence."""
    VERSION = 1
    def __init__(
        self, path: Path,
        *,
        manifest_sha256: str,
        direction: str,
        fence_generation: int, plan_digest: str,
    ):
        self.path = Path(path)
        self.identity = {
            "version": self.VERSION,
            "manifest_sha256": manifest_sha256,
            "direction": direction,
            "fence_generation": fence_generation,
            "plan_digest": plan_digest,
        }
        self.completed_batches: set[tuple[str, str, int, int]] = set()
        self._handle: Any = None
        self._open()
    def _open(self) -> None:
        if self.path.is_symlink():
            raise ExecutionSupportError("execution journal must not be a symlink")
        self.path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        if self.path.exists():
            records = _read_private_records(self.path)
            self._load_records(records)
            flags = os.O_WRONLY | os.O_APPEND
        else:
            flags = os.O_WRONLY | os.O_APPEND | os.O_CREAT | os.O_EXCL
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = os.open(self.path, flags, 0o600)
        self._handle = os.fdopen(descriptor, "a", encoding="utf-8")
        if not self.path.stat().st_mode & stat.S_IWUSR:
            self.close()
            raise ExecutionSupportError("execution journal is not owner-writable")
        if not self.path.exists():
            raise ExecutionSupportError("execution journal creation failed")
        if not self.completed_batches and self.path.stat().st_size == 0:
            self._append({"record": "header", **self.identity})
            _fsync_directory(self.path.parent)
    def _load_records(self, records: list[dict[str, Any]]) -> None:
        if not records or records[0] != {"record": "header", **self.identity}:
            raise ExecutionSupportError(
                "existing journal belongs to another manifest, direction, or fence"
            )
        for record in records[1:]:
            for key, value in self.identity.items():
                if record.get(key) != value:
                    raise ExecutionSupportError("journal record identity mismatch")
            if record.get("record") == "batch":
                key = _journal_batch_key(record)
                if key in self.completed_batches:
                    raise ExecutionSupportError("journal contains a duplicate batch record")
                self.completed_batches.add(key)
            elif record.get("record") != "verified":
                raise ExecutionSupportError("journal contains an unknown record type")
    def record_batch(
        self,
        *,
        kind: str,
        group_keys: tuple[str, ...],
        group_digest: str,
        first_ordinal: int,
        last_ordinal: int,
        change_count: int,
        sql_bytes: int,
    ) -> None:
        key = (kind, group_digest, first_ordinal, last_ordinal)
        if key in self.completed_batches:
            raise ExecutionSupportError("journal batch was already committed")
        self._append(
            {
                "record": "batch",
                **self.identity,
                "group_kind": kind,
                "group_keys": list(group_keys),
                "group_count": len(group_keys),
                "group_digest": group_digest,
                "first_ordinal": first_ordinal,
                "last_ordinal": last_ordinal,
                "change_count": change_count,
                "sql_bytes": sql_bytes,
            }
        )
        self.completed_batches.add(key)
    def record_verified(self, group_count: int, change_count: int) -> None:
        self._append(
            {
                "record": "verified",
                **self.identity,
                "group_count": group_count,
                "change_count": change_count,
            }
        )
    def _append(self, record: dict[str, Any]) -> None:
        assert self._handle is not None
        self._handle.write(canonical_json(record) + "\n")
        self._handle.flush()
        os.fsync(self._handle.fileno())
    def close(self) -> None:
        if self._handle is not None:
            self._handle.close()
            self._handle = None
    def __enter__(self) -> "ExecutionJournal":
        return self
    def __exit__(self, *_: object) -> None:
        self.close()
def _read_private_records(path: Path) -> list[dict[str, Any]]:
    info = path.stat()
    if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) & 0o077:
        raise ExecutionSupportError("execution journal must be a private regular file")
    if hasattr(os, "getuid") and info.st_uid != os.getuid():
        raise ExecutionSupportError("execution journal must be owned by current user")
    try:
        records = [
            json.loads(line)
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
    except (OSError, json.JSONDecodeError) as error:
        raise ExecutionSupportError("execution journal is unreadable or invalid") from error
    if any(not isinstance(record, dict) for record in records):
        raise ExecutionSupportError("execution journal records must be objects")
    return records
def _journal_batch_key(record: dict[str, Any]) -> tuple[str, str, int, int]:
    keys = record.get("group_keys")
    if (
        not isinstance(keys, list)
        or not keys
        or any(not isinstance(key, str) for key in keys)
        or record.get("group_count") != len(keys)
        or not isinstance(record.get("change_count"), int) or record["change_count"] <= 0
        or not isinstance(record.get("sql_bytes"), int) or record["sql_bytes"] <= 0
    ):
        raise ExecutionSupportError("journal batch shape is invalid")
    kind, digest = record.get("group_kind"), record.get("group_digest")
    first, last = record.get("first_ordinal"), record.get("last_ordinal")
    if (
        not isinstance(kind, str)
        or not isinstance(digest, str)
        or len(digest) != 64
        or not isinstance(first, int)
        or not isinstance(last, int)
        or not 0 < first <= last
    ):
        raise ExecutionSupportError("journal batch identity is invalid")
    return kind, digest, first, last
def _fsync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
