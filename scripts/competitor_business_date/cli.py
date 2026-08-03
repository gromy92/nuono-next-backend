"""Command-line contract for the governed historical date correction."""
from __future__ import annotations

import argparse
import re
from pathlib import Path


MAX_BATCH_SIZE = 5000
DEFAULT_BATCH_SIZE = 2000
MUTATIONS = {"fence-activate", "apply", "resume", "rollback", "fence-reopen"}


class CliError(RuntimeError):
    pass


def _mysql_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--mysql-defaults-file", type=Path, required=True)
    parser.add_argument("--schema", default="nuonuoai")


def _release_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--release-manifest", type=Path, required=True)
    parser.add_argument("--release-manifest-sha256", required=True)


def _target_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--expected-server-uuid", required=True)


def _fence_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--fence-generation", type=int, required=True)


def _lock_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--release-lock-file", type=Path, required=True)


def _manifest_arguments(
    parser: argparse.ArgumentParser,
    *,
    with_backup: bool = False,
) -> None:
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--manifest-sha256", required=True)
    if with_backup:
        parser.add_argument("--backup", type=Path, required=True)


def _verification_arguments(parser: argparse.ArgumentParser) -> None:
    _mysql_arguments(parser)
    _release_arguments(parser)
    _target_arguments(parser)
    _fence_arguments(parser)
    _lock_arguments(parser)
    _manifest_arguments(parser, with_backup=True)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="competitor-business-date-correction",
        description=(
            "Plan and execute the governed correction of legacy competitor "
            "Shanghai business dates."
        ),
    )
    commands = parser.add_subparsers(dest="command", required=True)

    preflight = commands.add_parser("preflight", help="Read-only scope and drift audit.")
    _mysql_arguments(preflight)
    _release_arguments(preflight)
    preflight.add_argument("--output", type=Path, required=True)

    status = commands.add_parser("fence-status", help="Read the persistent fence.")
    _mysql_arguments(status)

    activate = commands.add_parser(
        "fence-activate",
        help="Activate and drain the exact persistent maintenance generation.",
    )
    _mysql_arguments(activate)
    _release_arguments(activate)
    _target_arguments(activate)
    _fence_arguments(activate)
    _lock_arguments(activate)
    activate.add_argument("--actor", required=True)
    activate.add_argument("--execute", action="store_true")

    plan = commands.add_parser("plan", help="Freeze an immutable PRE/POST manifest.")
    _mysql_arguments(plan)
    _release_arguments(plan)
    _target_arguments(plan)
    _fence_arguments(plan)
    _lock_arguments(plan)
    plan.add_argument("--manifest", type=Path, required=True)
    plan.add_argument("--actor-user-id", type=int, required=True)
    plan.add_argument("--correction-time", required=True)
    plan.add_argument("--work-dir", type=Path, required=True)
    plan.add_argument("--clock-overrides", type=Path)
    plan.add_argument("--clock-overrides-sha256")

    backup = commands.add_parser("backup", help="Make a byte-exact scoped backup.")
    _manifest_arguments(backup)
    backup.add_argument("--backup", type=Path, required=True)

    verify = commands.add_parser(
        "verify",
        help="Verify every row under the same active persistent fence.",
    )
    _verification_arguments(verify)
    verify.add_argument(
        "--expected-state",
        choices=("post", "rollback"),
        required=True,
    )

    for name, help_text in (
        ("apply", "Apply PRE rows and no-op POST rows."),
        ("resume", "Resume the same active fenced apply run."),
        ("rollback", "Restore PRE rows before the same fence reopens."),
    ):
        command = commands.add_parser(name, help=help_text)
        _verification_arguments(command)
        command.add_argument("--journal", type=Path, required=True)
        command.add_argument("--execute", action="store_true")

    reopen = commands.add_parser(
        "fence-reopen",
        help="Verify the final state, then reopen this exact fence generation.",
    )
    _verification_arguments(reopen)
    reopen.add_argument("--actor", required=True)
    reopen.add_argument(
        "--expected-state",
        choices=("post", "rollback"),
        required=True,
    )
    reopen.add_argument("--execute", action="store_true")
    return parser


def validate_args(args: argparse.Namespace) -> None:
    if args.command in MUTATIONS and not args.execute:
        raise CliError("database mutation requires explicit --execute")
    if hasattr(args, "manifest_sha256"):
        _require_sha(args.manifest_sha256, "manifest")
    if hasattr(args, "release_manifest_sha256"):
        _require_sha(args.release_manifest_sha256, "release manifest")
    if hasattr(args, "fence_generation"):
        if args.fence_generation <= 0:
            raise CliError("fence generation must be a positive integer")
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}", args.run_id):
            raise CliError("run ID is invalid")
    if hasattr(args, "batch_size") and not 1 <= args.batch_size <= MAX_BATCH_SIZE:
        raise CliError(f"batch size must be between 1 and at most {MAX_BATCH_SIZE}")
    if hasattr(args, "expected_server_uuid") and not re.fullmatch(
        r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        args.expected_server_uuid,
    ):
        raise CliError("expected server UUID must be canonical lowercase UUID")
    if hasattr(args, "backup") and hasattr(args, "manifest"):
        if args.manifest.resolve() == args.backup.resolve():
            raise CliError("manifest and backup must be separate files")
    if args.command == "plan":
        paired = (args.clock_overrides is None, args.clock_overrides_sha256 is None)
        if paired not in {(True, True), (False, False)}:
            raise CliError("clock override file and SHA-256 must be supplied together")
        if args.clock_overrides_sha256:
            _require_sha(args.clock_overrides_sha256, "clock override")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    args = build_parser().parse_args(argv)
    validate_args(args)
    return args


def _require_sha(value: str, label: str) -> None:
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise CliError(f"{label} SHA-256 must be 64 lowercase hexadecimal characters")
