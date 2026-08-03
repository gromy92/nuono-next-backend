from __future__ import annotations

import stat
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.cli import CliError, build_parser, validate_args  # noqa: E402
from competitor_business_date.mysql_cli import (  # noqa: E402
    MysqlCliError,
    _redact_cli_error,
    build_mysql_command,
    validate_defaults_file,
)


UUID = "12345678-1234-1234-1234-123456789abc"
SHA = "a" * 64


def mutation_args(command: str = "apply") -> list[str]:
    values = [
        command,
        "--mysql-defaults-file", "/private/mysql.cnf",
        "--release-manifest", "/artifact/release-manifest.json",
        "--release-manifest-sha256", SHA,
        "--expected-server-uuid", UUID,
        "--run-id", "date-fix-1",
        "--fence-generation", "7",
        "--release-lock-file", "/private/.release-lock",
        "--manifest", "/private/manifest.sqlite",
        "--manifest-sha256", SHA,
        "--backup", "/separate/backup.sqlite",
    ]
    if command in {"apply", "resume", "rollback"}:
        values.extend(["--journal", f"/private/{command}.jsonl"])
    if command == "fence-reopen":
        values.extend(["--actor", "operator-307", "--expected-state", "post"])
    return values


class CompetitorBusinessDateCliTest(unittest.TestCase):
    def setUp(self):
        self.parser = build_parser()

    def test_cli_has_no_password_or_self_attested_writer_fence(self):
        subparsers = next(
            action for action in self.parser._actions if action.dest == "command"
        )
        help_text = "\n".join(
            [self.parser.format_help()]
            + [choice.format_help() for choice in subparsers.choices.values()]
        )
        self.assertNotIn("--password", help_text)
        self.assertNotIn("--writer-fenced", help_text)
        self.assertNotIn("--same-fence-generation", help_text)
        self.assertIn("fence-activate", help_text)
        self.assertIn("fence-reopen", help_text)

    def test_mutations_require_execute_and_exact_frozen_identity(self):
        for command in ("apply", "resume", "rollback", "fence-reopen"):
            with self.subTest(command=command):
                args = self.parser.parse_args(mutation_args(command))
                with self.assertRaisesRegex(CliError, "explicit --execute"):
                    validate_args(args)
                args.execute = True
                validate_args(args)

    def test_apply_rejects_same_backup_large_batch_and_bad_uuid(self):
        values = mutation_args()
        values[values.index("/separate/backup.sqlite")] = "/private/manifest.sqlite"
        values.append("--execute")
        with self.assertRaisesRegex(CliError, "separate files"):
            validate_args(self.parser.parse_args(values))

        values = mutation_args() + ["--batch-size", "5001", "--execute"]
        with self.assertRaisesRegex(CliError, "at most 5000"):
            validate_args(self.parser.parse_args(values))

        values = mutation_args()
        values[values.index(UUID)] = "wrong-server"
        values.append("--execute")
        with self.assertRaisesRegex(CliError, "canonical lowercase UUID"):
            validate_args(self.parser.parse_args(values))

    def test_plan_requires_override_file_and_digest_together(self):
        values = [
            "plan",
            "--mysql-defaults-file", "/private/mysql.cnf",
            "--release-manifest", "/artifact/release-manifest.json",
            "--release-manifest-sha256", SHA,
            "--expected-server-uuid", UUID,
            "--run-id", "date-fix-1",
            "--fence-generation", "7",
            "--release-lock-file", "/private/.release-lock",
            "--manifest", "/private/manifest.sqlite",
            "--actor-user-id", "307",
            "--correction-time", "2026-07-31 10:00:00",
            "--work-dir", "/private/work",
            "--clock-overrides", "/private/overrides.json",
        ]
        with self.assertRaisesRegex(CliError, "supplied together"):
            validate_args(self.parser.parse_args(values))

    def test_mysql_defaults_file_must_be_owned_private_regular_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "mysql.cnf"
            path.write_text("[client]\nuser=test\n", encoding="utf-8")
            path.chmod(0o644)
            with self.assertRaisesRegex(MysqlCliError, "permissions"):
                validate_defaults_file(path)
            path.chmod(0o600)
            self.assertEqual(path.resolve(), validate_defaults_file(path))
            link = Path(tmp) / "link.cnf"
            link.symlink_to(path)
            with self.assertRaisesRegex(MysqlCliError, "symbolic link"):
                validate_defaults_file(link)

    def test_mysql_command_uses_private_defaults_without_secret_arguments(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "mysql.cnf"
            path.write_text(
                "[client]\nuser=test\npassword=hidden\n",
                encoding="utf-8",
            )
            path.chmod(stat.S_IRUSR | stat.S_IWUSR)
            command = build_mysql_command(path, "nuonuoai")
            rendered = " ".join(command)
            self.assertIn(f"--defaults-file={path.resolve()}", rendered)
            for option in ("--no-login-paths", "--skip-reconnect", "--skip-force", "--quick"):
                self.assertIn(option, command)
            self.assertNotIn("hidden", rendered)
            self.assertNotIn("--password", rendered)

    def test_mysql_error_redacts_password_assignments(self):
        rendered = _redact_cli_error("access denied password = secret-value detail")
        self.assertNotIn("secret-value", rendered)
        self.assertIn("password=<redacted>", rendered)

    def test_mysql_command_omits_unsupported_no_login_paths_option(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "mysql.cnf"
            path.write_text("[client]\nuser=test\n", encoding="utf-8")
            path.chmod(stat.S_IRUSR | stat.S_IWUSR)

            command = build_mysql_command(
                path,
                "nuonuoai",
                no_login_paths_supported=False,
            )

        self.assertNotIn("--no-login-paths", command)
        self.assertIn("--skip-reconnect", command)


if __name__ == "__main__":
    unittest.main()
