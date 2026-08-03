#!/usr/bin/env python3
"""Plan and execute the release-side Nuono Database Migration Module."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.artifact import (  # noqa: E402
    verify_governed_jar,
    verify_release_inputs,
)
from schema_migrations.catalog import (  # noqa: E402
    load_catalog,
    load_catalog_from_jar,
)
from schema_migrations.core import (  # noqa: E402
    BASELINED_STATE,
    VALID_STATES,
    MigrationError,
    MigrationRunner,
    plan_migrations,
)
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402

DEFAULT_RESOURCE_ROOT = SCRIPT_DIR.parent / "src/main/resources"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "action",
        choices=("status", "plan", "apply", "repair-forward"),
    )
    parser.add_argument("--resource-root", type=Path, default=DEFAULT_RESOURCE_ROOT)
    parser.add_argument("--release-manifest", type=Path)
    parser.add_argument("--staged-jar", type=Path)
    parser.add_argument("--governed-jar-sha256")
    parser.add_argument("--mysql-defaults-file", type=Path, required=True)
    parser.add_argument("--expected-schema", required=True)
    parser.add_argument("--expected-host", required=True)
    parser.add_argument("--expected-port", type=int, required=True)
    parser.add_argument("--mysql-bin", default="mysql")
    parser.add_argument("--lock-timeout-seconds", type=int, default=30)
    parser.add_argument("--execution-timeout-seconds", type=int, default=300)
    parser.add_argument("--release-commit")
    parser.add_argument("--installed-by")
    parser.add_argument("--migration-key")
    parser.add_argument("--rerun", action="store_true")
    parser.add_argument("--approve-managed", action="append", default=[])
    parser.add_argument("--approve-runtime-drain", action="append", default=[])
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    database = None
    try:
        release_commit = None
        if args.action in {"apply", "repair-forward"}:
            release_commit = _required(args.release_commit, "--release-commit")
            migrations = _verified_mutation_catalog(args, release_commit)
        else:
            _reject_mutation_only_options(args)
            migrations = (
                load_catalog_from_jar(args.staged_jar)
                if args.staged_jar is not None
                else load_catalog(args.resource_root)
            )
        database = MySqlMigrationDatabase(
            args.mysql_defaults_file,
            expected_schema=args.expected_schema,
            expected_host=args.expected_host,
            expected_port=args.expected_port,
            mysql_bin=args.mysql_bin,
            execution_timeout_seconds=args.execution_timeout_seconds,
        )
        if args.action in {"status", "plan"}:
            return _report(args.action, migrations, database.load_states())

        installed_by = _required(
            args.installed_by or os.environ.get("USER"), "--installed-by"
        )
        runner = MigrationRunner(
            database,
            migrations,
            release_commit=release_commit,
            installed_by=installed_by,
            lock_timeout_seconds=args.lock_timeout_seconds,
        )
        if args.action == "apply":
            if args.migration_key or args.rerun:
                raise MigrationError(
                    "--migration-key/--rerun are only valid for repair-forward"
                )
            applied = runner.apply(
                approved_managed=args.approve_managed,
                approved_runtime_drains=args.approve_runtime_drain,
            )
            _emit({"result": "APPLIED", "migrations": applied})
            return 0

        migration_key = _required(args.migration_key, "--migration-key")
        result = runner.repair_forward(
            migration_key,
            rerun=args.rerun,
            approved_managed=args.approve_managed,
            approved_runtime_drains=args.approve_runtime_drain,
        )
        _emit(
            {
                "result": result,
                "migration_key": migration_key,
                "rerun": args.rerun,
            }
        )
        return 0
    except (MigrationError, OSError) as error:
        print(f"schema migration error: {error}", file=sys.stderr)
        return 2
    finally:
        if database is not None:
            database.close()


def _report(action, migrations, states) -> int:
    if action == "plan":
        pending = plan_migrations(migrations, states)
        _emit(
            {
                "result": "PENDING" if pending else "CURRENT",
                "migrations": [migration.key for migration in pending],
            }
        )
        return 0

    validation_error = None
    try:
        plan_migrations(migrations, states)
    except MigrationError as error:
        validation_error = str(error)
    items = []
    for index, migration in enumerate(migrations):
        state = states.get(migration.key)
        catalog_match = (
            state is None
            or (
                state.checksum == migration.checksum
                and state.postcheck_checksum == migration.postcheck_checksum
            )
        )
        state_valid = (
            state is None
            or (
                state.state in VALID_STATES
                and (
                    state.state != BASELINED_STATE
                    or (index == 0 and migration.kind == "BOOTSTRAP")
                )
            )
        )
        items.append(
            {
                "migration_key": migration.key,
                "state": "PENDING" if state is None else state.state,
                "checksum_sha256": migration.checksum,
                "attempt_no": 0 if state is None else state.attempt_no,
                "catalog_match": catalog_match,
                "state_valid": state_valid,
            }
        )
    catalog_keys = {migration.key for migration in migrations}
    for key in sorted(set(states) - catalog_keys):
        items.append(
            {
                "migration_key": key,
                "state": states[key].state,
                "attempt_no": states[key].attempt_no,
                "catalog_match": False,
                "state_valid": False,
            }
        )
    payload = {
        "result": "BLOCKED" if validation_error else "OK",
        "migrations": items,
    }
    if validation_error:
        payload["error"] = validation_error
    _emit(payload)
    return 2 if validation_error else 0


def _required(value: str | None, option: str) -> str:
    resolved = (value or "").strip()
    if not resolved:
        raise MigrationError(f"{option} is required for this action")
    return resolved


def _verified_mutation_catalog(args, release_commit):
    if args.staged_jar is None:
        raise MigrationError("mutating actions require --staged-jar")
    if args.release_manifest and args.governed_jar_sha256:
        raise MigrationError(
            "choose either --release-manifest or --governed-jar-sha256"
        )
    if args.release_manifest:
        return verify_release_inputs(
            args.release_manifest,
            args.staged_jar,
            release_commit,
            SCRIPT_DIR,
        )
    expected_sha = (args.governed_jar_sha256 or "").strip()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
        raise MigrationError(
            "mutating actions require a verified release manifest or "
            "--governed-jar-sha256 from the shared-lock cutover"
        )
    return verify_governed_jar(args.staged_jar, expected_sha, SCRIPT_DIR)


def _reject_mutation_only_options(args) -> None:
    if (
        args.release_manifest
        or args.governed_jar_sha256
        or args.release_commit
        or args.installed_by
        or args.migration_key
        or args.rerun
        or args.approve_managed
        or args.approve_runtime_drain
    ):
        raise MigrationError(
            "mutation/repair options are not valid for status or plan"
        )


def _emit(payload: dict[str, object]) -> None:
    print(json.dumps(payload, sort_keys=True, separators=(",", ":")))


if __name__ == "__main__":
    raise SystemExit(main())
