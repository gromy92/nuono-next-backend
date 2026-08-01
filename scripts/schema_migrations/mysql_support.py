from __future__ import annotations

import os
import queue
import re
import shutil
import stat
import subprocess
from pathlib import Path

from schema_migrations.model import MigrationError


def mysql_supports_no_login_paths(mysql_bin: str) -> bool:
    try:
        result = subprocess.run(
            [mysql_bin, "--no-login-paths", "--version"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise MigrationError("cannot inspect mysql client capabilities") from error
    if result.returncode == 0:
        return True
    output = (result.stderr or result.stdout).lower()
    if "unknown option" in output and "no-login-paths" in output:
        return False
    raise MigrationError(
        "mysql client capability probe failed before opening a database connection"
    )

MYSQL_ERROR = re.compile(
    r"ERROR (?P<code>[0-9]+) \((?P<sqlstate>[0-9A-Z]+)\)"
    r"(?: at line [0-9]+)?: (?P<message>.*)",
)


class MySqlExecutionError(MigrationError):
    def __init__(
        self,
        message: str,
        *,
        error_code: int | None = None,
        sqlstate: str | None = None,
    ):
        super().__init__(message)
        self.error_code = error_code
        self.sqlstate = sqlstate


def sql_literal(value: str) -> str:
    if "\x00" in value:
        raise MigrationError("SQL value contains a NUL byte")
    return (
        "CONVERT(X'"
        + value.encode("utf-8").hex()
        + "' USING utf8mb4) COLLATE utf8mb4_unicode_ci"
    )


def safe_error_summary(error: Exception) -> str:
    return _safe_message(str(error))


def mysql_error(output: str, returncode: int | None) -> MySqlExecutionError:
    summary = _safe_message(output)
    match = MYSQL_ERROR.search(summary)
    if match:
        code = int(match.group("code"))
        sqlstate = match.group("sqlstate")
        message = _safe_message(match.group("message"))
        return MySqlExecutionError(
            f"MySQL {code} ({sqlstate}): {message}",
            error_code=code,
            sqlstate=sqlstate,
        )
    suffix = f" (exit {returncode})" if returncode is not None else ""
    return MySqlExecutionError(
        "mysql connection ended before command confirmation"
        + suffix
        + (f": {summary}" if summary else "")
    )


def validate_identity(
    line: str,
    expected_schema: str,
    prior_server_uuid: str | None,
) -> str:
    columns = line.split("\t")
    if len(columns) != 2:
        raise MigrationError(f"invalid mysql target identity: {line!r}")
    schema_hex, server_uuid = columns
    expected_hex = expected_schema.encode("utf-8").hex().upper()
    if schema_hex.upper() != expected_hex:
        raise MigrationError(
            "mysql target schema mismatch: "
            f"expected {expected_schema!r}, got hex {schema_hex!r}"
        )
    if not re.fullmatch(r"[0-9a-fA-F-]{32,64}", server_uuid):
        raise MigrationError(f"invalid mysql server UUID: {server_uuid!r}")
    normalized = server_uuid.lower()
    if prior_server_uuid is not None and prior_server_uuid != normalized:
        raise MigrationError("mysql target server changed during migration")
    return normalized


def validate_target_options(
    expected_schema: str,
    expected_host: str,
    expected_port: int,
    execution_timeout_seconds: int,
) -> None:
    if not expected_schema or len(expected_schema) > 64 or "\x00" in expected_schema:
        raise MigrationError("expected schema must contain 1-64 non-NUL characters")
    if (
        not expected_host
        or len(expected_host) > 255
        or any(character.isspace() for character in expected_host)
        or "\x00" in expected_host
    ):
        raise MigrationError("expected host must be a valid non-empty host")
    if expected_port < 1 or expected_port > 65535:
        raise MigrationError("expected port must be between 1 and 65535")
    if execution_timeout_seconds < 1 or execution_timeout_seconds > 1800:
        raise MigrationError("execution timeout must be between 1 and 1800 seconds")


def read_session_output(
    process: subprocess.Popen[str],
    line_queue: queue.Queue[str | None],
) -> None:
    assert process.stdout is not None
    try:
        for line in process.stdout:
            line_queue.put(line.rstrip("\r\n"))
    finally:
        process.wait()
        line_queue.put(None)


def freeze_defaults_file(source_path: Path, target_path: Path) -> None:
    try:
        metadata = source_path.lstat()
    except OSError as error:
        raise MigrationError(
            f"mysql defaults file does not exist: {source_path}"
        ) from error
    _validate_defaults_metadata(metadata)
    source_fd = None
    try:
        source_fd = os.open(
            source_path,
            os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
        )
        opened = os.fstat(source_fd)
        _validate_defaults_metadata(opened)
        if (opened.st_dev, opened.st_ino) != (metadata.st_dev, metadata.st_ino):
            raise MigrationError("mysql defaults file changed while opening")
        target_fd = os.open(
            target_path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL,
            0o600,
        )
    except BaseException:
        if source_fd is not None:
            os.close(source_fd)
        raise
    with os.fdopen(source_fd, "rb") as source, os.fdopen(
        target_fd, "wb"
    ) as target:
        shutil.copyfileobj(source, target)


def _validate_defaults_metadata(metadata: os.stat_result) -> None:
    if not stat.S_ISREG(metadata.st_mode):
        raise MigrationError("mysql defaults file must be a regular file")
    if metadata.st_size > 64 * 1024:
        raise MigrationError("mysql defaults file is unexpectedly large")
    if os.name != "nt":
        if metadata.st_mode & 0o077:
            raise MigrationError(
                "mysql defaults file must not be group/world accessible"
            )
        if metadata.st_uid != os.getuid():
            raise MigrationError("mysql defaults file must be owned by this user")


def _safe_message(value: str) -> str:
    compact = " ".join(value.replace("\x00", "").split())
    compact = re.sub(
        r"(?i)(password|passwd|pwd)\s*=\s*[^\s;]+",
        r"\1=<redacted>",
        compact,
    )
    return compact[-1000:]
