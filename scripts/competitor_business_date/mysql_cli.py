"""Small, credential-safe MySQL CLI adapter for governed correction SQL."""
from __future__ import annotations

import base64
import json
import os
import re
import stat
import subprocess
import tempfile
import threading
from pathlib import Path
from typing import Any, Iterable, Iterator


class MysqlCliError(RuntimeError):
    pass


def validate_defaults_file(path: Path) -> Path:
    candidate = Path(path)
    if candidate.is_symlink():
        raise MysqlCliError(f"MySQL defaults file must not be a symbolic link: {candidate}")
    try:
        info = candidate.stat()
    except FileNotFoundError as error:
        raise MysqlCliError(f"MySQL defaults file does not exist: {candidate}") from error
    if not stat.S_ISREG(info.st_mode):
        raise MysqlCliError(f"MySQL defaults file must be regular: {candidate}")
    if hasattr(os, "getuid") and info.st_uid != os.getuid():
        raise MysqlCliError("MySQL defaults file must be owned by the current user")
    if stat.S_IMODE(info.st_mode) & 0o077:
        raise MysqlCliError("MySQL defaults file permissions must be 0600 or stricter")
    return candidate.resolve()


def build_mysql_command(defaults_file: Path, schema: str) -> list[str]:
    defaults = validate_defaults_file(defaults_file)
    if not re.fullmatch(r"[A-Za-z0-9_]+", schema):
        raise MysqlCliError(f"unsafe MySQL schema name: {schema!r}")
    return [
        "mysql",
        f"--defaults-file={defaults}",
        "--no-login-paths",
        f"--database={schema}",
        "--batch",
        "--quick",
        "--skip-column-names",
        "--default-character-set=utf8mb4",
        "--show-warnings",
        "--skip-reconnect",
        "--skip-force",
    ]


class MysqlCli:
    def __init__(
        self,
        defaults_file: Path,
        schema: str,
        *,
        timeout_seconds: int = 60,
    ):
        self.defaults_file = validate_defaults_file(defaults_file)
        self.schema = schema
        self.timeout_seconds = timeout_seconds
        self.command = build_mysql_command(self.defaults_file, schema)

    def run_script(self, sql: str, *, timeout_seconds: int | None = None) -> str:
        if "\x00" in sql:
            raise MysqlCliError("SQL template contains a NUL byte")
        environment = dict(os.environ)
        environment["MYSQL_HISTFILE"] = os.devnull
        result = subprocess.run(
            self.command,
            input=sql,
            text=True,
            capture_output=True,
            env=environment,
            timeout=timeout_seconds or self.timeout_seconds,
            check=False,
        )
        if result.returncode != 0:
            detail = _redact_cli_error(result.stderr)
            raise MysqlCliError(
                f"MySQL command failed with exit {result.returncode}: {detail}"
            )
        return result.stdout

    def query_json_objects(
        self,
        sql: str,
        *,
        timeout_seconds: int | None = None,
    ) -> Iterator[dict[str, Any]]:
        if "\x00" in sql:
            raise MysqlCliError("SQL template contains a NUL byte")
        environment = dict(os.environ)
        environment["MYSQL_HISTFILE"] = os.devnull
        with tempfile.TemporaryFile(mode="w+t", encoding="utf-8") as errors:
            process = subprocess.Popen(
                self.command,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=errors,
                text=True,
                env=environment,
            )
            timed_out = threading.Event()

            def expire() -> None:
                if process.poll() is None:
                    timed_out.set()
                    process.kill()

            timer = threading.Timer(
                timeout_seconds or self.timeout_seconds,
                expire,
            )
            timer.daemon = True
            timer.start()
            try:
                assert process.stdin is not None
                assert process.stdout is not None
                process.stdin.write(sql)
                process.stdin.close()
                yield from decode_base64_json_lines(process.stdout)
                return_code = process.wait()
                if timed_out.is_set():
                    raise MysqlCliError("MySQL command exceeded its wall-clock timeout")
                if return_code != 0:
                    errors.seek(0)
                    raise MysqlCliError(
                        f"MySQL command failed with exit {return_code}: "
                        f"{_redact_cli_error(errors.read())}"
                    )
            except BaseException:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
                raise
            finally:
                timer.cancel()

    def query_one_json(self, sql: str) -> dict[str, Any]:
        rows = list(self.query_json_objects(sql))
        if len(rows) != 1:
            raise MysqlCliError(f"expected exactly one query row, found {len(rows)}")
        return rows[0]


def decode_base64_json_lines(lines: Iterable[str]) -> Iterator[dict[str, Any]]:
    for line_number, raw in enumerate(lines, start=1):
        value = raw.strip()
        if not value:
            continue
        try:
            decoded = base64.b64decode(value, validate=True)
            row = json.loads(decoded)
        except (ValueError, json.JSONDecodeError) as error:
            raise MysqlCliError(
                f"invalid encoded MySQL row at output line {line_number}"
            ) from error
        if not isinstance(row, dict):
            raise MysqlCliError(
                f"encoded MySQL row {line_number} must contain a JSON object"
            )
        yield row


def encoded_json_select(json_expression: str, from_clause: str) -> str:
    """Return a one-column query whose rows survive tabs/newlines losslessly."""
    if ";" in json_expression:
        raise MysqlCliError("JSON projection must not contain a statement separator")
    return (
        "SELECT REPLACE(TO_BASE64(CAST("
        + json_expression
        + " AS CHAR CHARACTER SET utf8mb4)), '\\n', '') "
        + from_clause.rstrip().rstrip(";")
        + ";\n"
    )


def _redact_cli_error(stderr: str) -> str:
    compact = " ".join(stderr.strip().split())
    compact = re.sub(
        r"(?i)(password|passwd|pwd)\s*[=:]\s*\S+",
        r"\1=<redacted>",
        compact,
    )
    return compact[-2000:] or "no stderr"
