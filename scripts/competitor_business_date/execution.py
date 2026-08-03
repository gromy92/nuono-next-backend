"""Governed, resumable execution of an immutable correction manifest."""
from __future__ import annotations
import filecmp
from contextlib import ExitStack
from pathlib import Path
from typing import Any, Callable
from .apply_sql import APPLY, ROLLBACK, build_batch_sql, expected_success_sentinel
from .execution_plan import (
    ExecutionBatch, ExecutionPlanError, execution_plan_digest, load_batch,
    ordered_groups, prevalidate_batches, resolve_max_sql_bytes, validate_batch_size,
)
from .execution_support import ExecutionJournal, build_verify_sql, verify_sentinel
from .evidence_contract import validate_source_evidence
from .governance import assert_database_writer_fence
from .manifest import ManifestChange, ManifestReader
from .manifest_contract import validate_manifest_changes, validate_manifest_metadata
from .mysql_cli import MysqlCli
from .preflight import read_schema_fingerprint, validate_target_schema
OPERATIONS = {"apply": APPLY, "resume": APPLY, "rollback": ROLLBACK}
ExecutionError = ExecutionPlanError
def execute_manifest(
    mysql: MysqlCli,
    advisory_lock: Any,
    *,
    assert_outer_fence_held: Callable[[], None],
    manifest: Path,
    manifest_sha256: str,
    backup: Path,
    fence_generation: int,
    expected_run_id: str,
    expected_server_uuid: str,
    release_provenance: dict[str, Any],
    journal: Path,
    operation: str,
    batch_size: int,
    max_sql_bytes: int | None = None,
    timeout_seconds: int = 1800,
) -> dict[str, Any]:
    """Execute while the caller keeps one release/advisory/writer fence held."""
    direction = OPERATIONS.get(operation)
    if direction is None:
        raise ExecutionError("operation must be apply, resume, or rollback")
    validate_batch_size(batch_size)
    with ExitStack() as stack:
        reader = stack.enter_context(ManifestReader(Path(manifest), manifest_sha256))
        backup_reader = stack.enter_context(ManifestReader(Path(backup), manifest_sha256))
        _validate_assets(reader, backup_reader, manifest, backup)
        _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
        _validate_metadata(
            reader,
            mysql,
            fence_generation,
            expected_run_id,
            expected_server_uuid,
            release_provenance,
        )
        validate_source_evidence(reader, reader.metadata)
        contract_counts = validate_manifest_changes(
            reader.iter_changes(), reader.metadata
        )
        sql_limit = resolve_max_sql_bytes(reader.metadata, max_sql_bytes)
        groups = ordered_groups(reader, direction)
        _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
        batches, total_changes, max_group_size, max_group_sql = prevalidate_batches(
            reader, groups, batch_size=batch_size, max_sql_bytes=sql_limit,
            direction=direction,
        )
        _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
        if not batches:
            raise ExecutionError("manifest contains no correction batches")
        plan_digest = execution_plan_digest(batches)
        journal_writer = stack.enter_context(ExecutionJournal(
            Path(journal), manifest_sha256=manifest_sha256,
            direction=direction, fence_generation=fence_generation,
            plan_digest=plan_digest,
        ))
        executed = resumed = 0
        for batch in batches:
            changes = load_batch(
                reader, batch, batch_size=batch_size,
                max_sql_bytes=sql_limit, direction=direction,
            )
            _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
            if batch.journal_key in journal_writer.completed_batches:
                _run_verify(mysql, changes, direction, timeout_seconds)
                resumed += 1
                continue
            output = mysql.run_script(
                build_batch_sql(changes, direction=direction),
                timeout_seconds=timeout_seconds,
            )
            _require_sentinel(
                output, expected_success_sentinel(changes, direction=direction)
            )
            _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
            _record_batch(journal_writer, batch)
            executed += 1
        verified = _verify_all(
            mysql, advisory_lock, assert_outer_fence_held, reader, batches,
            direction, batch_size, sql_limit, timeout_seconds,
        )
        if verified != total_changes:
            raise ExecutionError("final verification did not cover every manifest change")
        journal_writer.record_verified(len(batches), verified)
        return {
            "operation": operation, "direction": direction,
            "manifest_sha256": manifest_sha256,
            "fence_generation": fence_generation,
            "batch_count": len(batches), "group_count": len(groups),
            "change_count": total_changes, "max_group_size": max_group_size,
            "max_group_sql_bytes": max_group_sql, "max_sql_bytes": sql_limit,
            "execution_plan_digest": plan_digest,
            "contract_change_counts": contract_counts,
            "executed_batches": executed, "resumed_batches": resumed,
            "verified_changes": verified,
            "final_state": "POST" if direction == APPLY else "PRE_WITH_SEQUENCE_POST",
        }
def verify_manifest(
    mysql: MysqlCli,
    advisory_lock: Any,
    *,
    assert_outer_fence_held: Callable[[], None],
    manifest: Path,
    manifest_sha256: str,
    fence_generation: int,
    expected_run_id: str,
    expected_server_uuid: str,
    release_provenance: dict[str, Any],
    expected_direction: str,
    batch_size: int,
    max_sql_bytes: int | None = None,
    backup: Path | None = None,
    timeout_seconds: int = 1800,
) -> dict[str, Any]:
    """Verify every row against one exact state under the same outer fence."""
    validate_batch_size(batch_size)
    if expected_direction not in {APPLY, ROLLBACK}:
        raise ExecutionError("expected direction must be apply or rollback")
    with ExitStack() as stack:
        reader = stack.enter_context(ManifestReader(Path(manifest), manifest_sha256))
        if backup is not None:
            copy = stack.enter_context(ManifestReader(Path(backup), manifest_sha256))
            _validate_assets(reader, copy, manifest, backup)
        _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
        _validate_metadata(
            reader,
            mysql,
            fence_generation,
            expected_run_id,
            expected_server_uuid,
            release_provenance,
        )
        validate_source_evidence(reader, reader.metadata)
        contract_counts = validate_manifest_changes(
            reader.iter_changes(), reader.metadata
        )
        sql_limit = resolve_max_sql_bytes(reader.metadata, max_sql_bytes)
        groups = ordered_groups(reader, expected_direction)
        _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
        batches, change_count, max_group, max_group_sql = prevalidate_batches(
            reader, groups, batch_size=batch_size, max_sql_bytes=sql_limit,
            direction=expected_direction,
        )
        _assert_fence(mysql, advisory_lock, assert_outer_fence_held)
        verified = _verify_all(
            mysql, advisory_lock, assert_outer_fence_held, reader, batches,
            expected_direction, batch_size, sql_limit, timeout_seconds,
        )
        if verified != change_count:
            raise ExecutionError("verification did not cover every manifest change")
        return {
            "expected_state": (
                "POST" if expected_direction == APPLY else "PRE_WITH_SEQUENCE_POST"
            ),
            "batch_count": len(batches), "group_count": len(groups),
            "max_group_size": max_group, "verified_changes": verified,
            "max_group_sql_bytes": max_group_sql, "max_sql_bytes": sql_limit,
            "contract_change_counts": contract_counts,
        }
def _verify_all(
    mysql: MysqlCli, advisory_lock: Any, outer_fence: Callable[[], None],
    reader: ManifestReader, batches: tuple[ExecutionBatch, ...], direction: str,
    batch_size: int, max_sql_bytes: int, timeout_seconds: int,
) -> int:
    verified = 0
    for batch in batches:
        changes = load_batch(
            reader, batch, batch_size=batch_size,
            max_sql_bytes=max_sql_bytes, direction=direction,
        )
        _assert_fence(mysql, advisory_lock, outer_fence)
        _run_verify(mysql, changes, direction, timeout_seconds)
        verified += len(changes)
        _assert_fence(mysql, advisory_lock, outer_fence)
    return verified
def _run_verify(
    mysql: MysqlCli, changes: tuple[ManifestChange, ...],
    direction: str, timeout_seconds: int,
) -> None:
    output = mysql.run_script(
        build_verify_sql(changes, direction=direction),
        timeout_seconds=timeout_seconds,
    )
    _require_sentinel(output, verify_sentinel(direction, len(changes)))
def _record_batch(journal: ExecutionJournal, batch: ExecutionBatch) -> None:
    descriptor = batch.descriptor
    journal.record_batch(
        kind=descriptor.group_kind, group_keys=descriptor.group_keys,
        group_digest=descriptor.group_digest, first_ordinal=batch.first_ordinal,
        last_ordinal=batch.last_ordinal, change_count=descriptor.change_count,
        sql_bytes=batch.sql_bytes,
    )
def _validate_assets(
    reader: ManifestReader, backup_reader: ManifestReader, manifest: Path, backup: Path
) -> None:
    source, copy = Path(manifest).resolve(), Path(backup).resolve()
    if source == copy or source.samefile(copy):
        raise ExecutionError("backup must be a separate file and inode")
    if not filecmp.cmp(source, copy, shallow=False):
        raise ExecutionError("backup is not a byte-exact manifest copy")
    if reader.metadata != backup_reader.metadata:
        raise ExecutionError("backup manifest metadata differs from source")
def _validate_metadata(
    reader: ManifestReader,
    mysql: MysqlCli,
    fence_generation: int,
    expected_run_id: str,
    expected_server_uuid: str,
    release_provenance: dict[str, Any],
) -> None:
    metadata = reader.metadata
    validate_manifest_metadata(metadata)
    if metadata.get("target_schema") != mysql.schema:
        raise ExecutionError("manifest target schema differs from MySQL target")
    if (
        isinstance(fence_generation, bool) or not isinstance(fence_generation, int)
        or fence_generation <= 0
        or metadata.get("fence_generation") != fence_generation
    ):
        raise ExecutionError("manifest fence generation mismatch")
    if metadata.get("run_id") != expected_run_id:
        raise ExecutionError("manifest run ID differs from active fence run")
    identity = metadata.get("database_identity")
    if (
        not isinstance(identity, dict)
        or identity.get("server_uuid") != expected_server_uuid
    ):
        raise ExecutionError("manifest database UUID differs from approved target")
    if metadata.get("release_artifact") != release_provenance:
        raise ExecutionError("release artifact provenance differs from manifest")
    source = metadata.get("source_fingerprint")
    if not isinstance(source, dict) or source.get("schema_state") != "TARGET":
        raise ExecutionError("manifest source fingerprint metadata is invalid")
    rows, current_sha = read_schema_fingerprint(mysql)
    if validate_target_schema(rows, expected_schema=mysql.schema) != "TARGET":
        raise ExecutionError("current schema is not migration 240 TARGET state")
    if source.get("sha256") != current_sha:
        raise ExecutionError("current schema fingerprint differs from manifest source")
def _assert_fence(mysql: MysqlCli, advisory_lock: Any,
                  outer_fence: Callable[[], None]) -> None:
    outer_fence()
    advisory_lock.assert_held()
    holder = getattr(advisory_lock, "connection_id", None)
    if isinstance(holder, bool) or not isinstance(holder, int) or holder <= 0:
        raise ExecutionError("advisory lock has no valid holder connection ID")
    assert_database_writer_fence(mysql, holder)
def _require_sentinel(output: str, expected: str) -> None:
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if lines != [expected]:
        raise ExecutionError(
            f"database batch did not return its unique success sentinel: {lines[:3]}"
        )
