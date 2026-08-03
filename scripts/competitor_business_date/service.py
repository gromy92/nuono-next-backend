"""Command dispatch for the governed correction lifecycle."""
from __future__ import annotations

import json
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Iterator

from .apply_sql import APPLY, ROLLBACK
from .execution import execute_manifest, verify_manifest
from .governance import (
    MysqlAdvisoryLock,
    ReleaseFileLock,
    assert_database_writer_fence,
    assert_no_backend_jvm,
)
from .manifest import copy_manifest_backup
from .mysql_cli import MysqlCli
from .overrides import load_clock_overrides
from .persistent_fence import (
    activate_fence,
    assert_fence_active,
    read_fence,
    reopen_fence,
)
from .plan_service import freeze_manifest
from .preflight import (
    read_schema_fingerprint,
    require_server_uuid,
    run_read_only_preflight,
    validate_target_schema,
)
from .release_artifact import load_release_provenance


class ServiceError(RuntimeError):
    pass


def run_command(args: Any) -> int:
    mysql = _mysql(args) if hasattr(args, "mysql_defaults_file") else None
    if args.command == "fence-status":
        _print(read_fence(mysql))
        return 0
    if args.command == "backup":
        digest = copy_manifest_backup(
            args.manifest,
            args.backup,
            args.manifest_sha256,
        )
        _print({"backup": str(args.backup.resolve()), "sha256": digest})
        return 0

    provenance = _release_provenance(args)
    if args.command == "preflight":
        report = run_read_only_preflight(
            mysql,
            args.output,
            now=datetime.now().replace(microsecond=0),
            release_provenance=provenance,
        )
        _print(report)
        return 0
    if args.command == "fence-activate":
        _require_target(mysql, args.expected_server_uuid)
        with ReleaseFileLock(args.release_lock_file), MysqlAdvisoryLock(mysql):
            sentinel = activate_fence(
                mysql,
                generation=args.fence_generation,
                run_id=args.run_id,
                actor=args.actor,
            )
        _print({"result": sentinel, "release_artifact": provenance})
        return 0
    if args.command == "plan":
        report = _plan(mysql, args, provenance)
        _print(report)
        return 0
    if args.command in {"apply", "resume", "rollback"}:
        report = _mutate(mysql, args, provenance)
        _print(report)
        return 0
    if args.command == "verify":
        report = _verify(mysql, args, provenance)
        _print(report)
        return 0
    if args.command == "fence-reopen":
        report = _reopen(mysql, args, provenance)
        _print(report)
        return 0
    raise ServiceError(f"unsupported correction command: {args.command}")


def _plan(mysql: MysqlCli, args: Any, provenance: dict[str, Any]) -> dict[str, Any]:
    overrides = (
        load_clock_overrides(
            args.clock_overrides,
            args.clock_overrides_sha256,
        )
        if args.clock_overrides
        else None
    )
    correction_time = _parse_time(args.correction_time)
    with _governed(mysql, args) as (advisory, outer):
        def plan_fence() -> None:
            outer()
            advisory.assert_held()
            assert_database_writer_fence(mysql, advisory.connection_id)

        seal, summary = freeze_manifest(
            mysql,
            args.manifest,
            actor_user_id=args.actor_user_id,
            fence_generation=args.fence_generation,
            run_id=args.run_id,
            correction_time=correction_time,
            fence_check=plan_fence,
            expected_server_uuid=args.expected_server_uuid,
            work_dir=args.work_dir,
            release_provenance=provenance,
            clock_overrides=overrides,
        )
    return {
        "manifest": str(seal.path),
        "manifest_sha256": seal.file_sha256,
        "content_digest": seal.content_digest,
        "summary": summary,
        "production_write_authorized": False,
    }


def _mutate(mysql: MysqlCli, args: Any, provenance: dict[str, Any]) -> dict[str, Any]:
    with _governed(mysql, args) as (advisory, outer):
        return execute_manifest(
            mysql,
            advisory,
            assert_outer_fence_held=outer,
            manifest=args.manifest,
            manifest_sha256=args.manifest_sha256,
            backup=args.backup,
            fence_generation=args.fence_generation,
            expected_run_id=args.run_id,
            expected_server_uuid=args.expected_server_uuid,
            release_provenance=provenance,
            journal=args.journal,
            operation=args.command,
            batch_size=args.batch_size,
        )


def _verify(mysql: MysqlCli, args: Any, provenance: dict[str, Any]) -> dict[str, Any]:
    direction = APPLY if args.expected_state == "post" else ROLLBACK
    with _governed(mysql, args) as (advisory, outer):
        return verify_manifest(
            mysql,
            advisory,
            assert_outer_fence_held=outer,
            manifest=args.manifest,
            manifest_sha256=args.manifest_sha256,
            backup=args.backup,
            fence_generation=args.fence_generation,
            expected_run_id=args.run_id,
            expected_server_uuid=args.expected_server_uuid,
            release_provenance=provenance,
            expected_direction=direction,
            batch_size=args.batch_size,
        )


def _reopen(mysql: MysqlCli, args: Any, provenance: dict[str, Any]) -> dict[str, Any]:
    with _governed(mysql, args) as (advisory, outer):
        verification = verify_manifest(
            mysql,
            advisory,
            assert_outer_fence_held=outer,
            manifest=args.manifest,
            manifest_sha256=args.manifest_sha256,
            backup=args.backup,
            fence_generation=args.fence_generation,
            expected_run_id=args.run_id,
            expected_server_uuid=args.expected_server_uuid,
            release_provenance=provenance,
            expected_direction=(
                APPLY if args.expected_state == "post" else ROLLBACK
            ),
            batch_size=args.batch_size,
        )
        outer()
        advisory.assert_held()
        assert_database_writer_fence(mysql, advisory.connection_id)
        sentinel = reopen_fence(
            mysql,
            generation=args.fence_generation,
            run_id=args.run_id,
            actor=args.actor,
        )
    return {"result": sentinel, "verification": verification}


@contextmanager
def _governed(
    mysql: MysqlCli,
    args: Any,
) -> Iterator[tuple[MysqlAdvisoryLock, Callable[[], None]]]:
    with ReleaseFileLock(args.release_lock_file), MysqlAdvisoryLock(mysql) as advisory:
        def outer() -> None:
            assert_fence_active(
                mysql,
                generation=args.fence_generation,
                run_id=args.run_id,
            )
            assert_no_backend_jvm()

        outer()
        yield advisory, outer
        outer()


def _require_target(mysql: MysqlCli, expected_uuid: str) -> None:
    rows, _ = read_schema_fingerprint(mysql)
    if validate_target_schema(rows, expected_schema=mysql.schema) != "TARGET":
        raise ServiceError("migrations 240 and 241 exact TARGET state is required")
    require_server_uuid(rows, expected_uuid)


def _mysql(args: Any) -> MysqlCli:
    return MysqlCli(args.mysql_defaults_file, args.schema)


def _release_provenance(args: Any) -> dict[str, Any]:
    return load_release_provenance(
        args.release_manifest,
        args.release_manifest_sha256,
    )


def _parse_time(value: str) -> datetime:
    try:
        return datetime.strptime(value, "%Y-%m-%d %H:%M:%S")
    except ValueError as error:
        raise ServiceError(
            "correction time must be YYYY-MM-DD HH:MM:SS in Shanghai local time"
        ) from error


def _print(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))
