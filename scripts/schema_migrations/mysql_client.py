from __future__ import annotations

import os
import queue
import secrets
import subprocess
import tempfile
import threading
import time
from pathlib import Path

from schema_migrations.model import MigrationError
from schema_migrations.mysql_support import (
    MySqlExecutionError,
    freeze_defaults_file,
    mysql_error,
    read_session_output,
    sql_literal,
    validate_identity,
    validate_target_options,
)


class MySqlClient:
    def __init__(
        self,
        defaults_file: Path,
        *,
        expected_schema: str,
        expected_host: str,
        expected_port: int,
        mysql_bin: str = "mysql",
        execution_timeout_seconds: int = 300,
    ):
        validate_target_options(
            expected_schema,
            expected_host,
            expected_port,
            execution_timeout_seconds,
        )
        self.source_defaults_file = Path(
            os.path.abspath(os.fspath(defaults_file))
        )
        self.expected_schema = expected_schema
        self.expected_host = expected_host
        self.expected_port = expected_port
        self.mysql_bin = mysql_bin
        self.execution_timeout_seconds = execution_timeout_seconds
        self.server_uuid: str | None = None
        self.lock_process: subprocess.Popen[str] | None = None
        self._reader: threading.Thread | None = None
        self._lines: queue.Queue[str | None] | None = None
        self._temporary = tempfile.TemporaryDirectory(
            prefix="nuono-schema-migration-client-"
        )
        self.defaults_file = Path(self._temporary.name) / "mysql.cnf"
        try:
            freeze_defaults_file(self.source_defaults_file, self.defaults_file)
            self.no_login_paths_supported = self._detect_no_login_paths_support()
        except BaseException:
            self._temporary.cleanup()
            raise

    def acquire_lock(self, lock_name: str, timeout_seconds: int) -> None:
        if self.lock_process is not None:
            raise MigrationError("database migration lock is already held")
        process = subprocess.Popen(
            self.command("--unbuffered"),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        if process.stdin is None or process.stdout is None:
            process.kill()
            process.wait(timeout=5)
            raise MigrationError("cannot open persistent mysql migration connection")
        self.lock_process = process
        self._lines = queue.Queue()
        line_queue = self._lines
        self._reader = threading.Thread(
            target=read_session_output,
            args=(process, line_queue),
            name="nuono-mysql-migration-reader",
            daemon=True,
        )
        self._reader.start()
        try:
            output = self._session_execute(
                self._session_preamble()
                + "SELECT HEX(DATABASE()), @@server_uuid;\n"
                + f"SELECT GET_LOCK({sql_literal(lock_name)}, {timeout_seconds});\n",
                timeout_seconds=max(20, timeout_seconds + 15),
            )
            lines = output.splitlines()
            if len(lines) != 2:
                raise MigrationError(
                    f"unexpected mysql lock confirmation: {lines!r}"
                )
            self._validate_identity_line(lines[0])
            if lines[1] != "1":
                raise MigrationError(
                    f"database migration lock was not acquired: {lines[1]!r}"
                )
        except Exception:
            self._terminate_session()
            raise

    def release_lock(self, lock_name: str) -> None:
        process = self.lock_process
        if process is None:
            return
        try:
            if process.poll() is None:
                self._session_execute(
                    f"DO RELEASE_LOCK({sql_literal(lock_name)});\n",
                    timeout_seconds=5,
                )
                if process.stdin is not None:
                    process.stdin.write("quit\n")
                    process.stdin.flush()
                    process.stdin.close()
                process.wait(timeout=5)
        except (BrokenPipeError, MigrationError, subprocess.TimeoutExpired):
            self._terminate_session()
        finally:
            self._close_session_handles()

    def execute(self, sql: str, *, timeout_seconds: int | None = None) -> str:
        if self.lock_process is not None:
            if self.lock_process.poll() is not None:
                raise MigrationError("database migration lock session was lost")
            return self._session_execute(
                sql,
                timeout_seconds=timeout_seconds or self.execution_timeout_seconds,
            )
        return self._execute_once(
            sql,
            timeout_seconds=timeout_seconds or self.execution_timeout_seconds,
        )

    def execute_recovery(
        self,
        sql: str,
        *,
        timeout_seconds: int | None = None,
    ) -> str:
        return self._execute_once(
            sql,
            timeout_seconds=timeout_seconds or self.execution_timeout_seconds,
        )

    def execute_readonly(
        self,
        sql: str,
        *,
        timeout_seconds: int | None = None,
    ) -> str:
        process = self.lock_process
        if process is not None and process.poll() is not None:
            raise MigrationError("database migration lock session was lost")
        result = self._execute_once(
            sql,
            timeout_seconds=timeout_seconds or self.execution_timeout_seconds,
        )
        if process is not None and process.poll() is not None:
            raise MigrationError("database migration lock session was lost")
        return result

    def close(self) -> None:
        if self.lock_process is not None:
            self._terminate_session()
        self._temporary.cleanup()

    def command(self, *extra: str) -> list[str]:
        login_path_option = (
            ["--no-login-paths"] if self.no_login_paths_supported else []
        )
        return [
            self.mysql_bin,
            f"--defaults-file={self.defaults_file}",
            *login_path_option,
            "--skip-reconnect",
            "--protocol=TCP",
            f"--host={self.expected_host}",
            f"--port={self.expected_port}",
            f"--database={self.expected_schema}",
            "--batch",
            "--skip-column-names",
            "--raw",
            "--default-character-set=utf8mb4",
            "--connect-timeout=10",
            *extra,
        ]

    def _detect_no_login_paths_support(self) -> bool:
        try:
            result = subprocess.run(
                [self.mysql_bin, "--no-login-paths", "--version"],
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

    def _execute_once(self, sql: str, *, timeout_seconds: int) -> str:
        payload = (
            self._session_preamble()
            + "SELECT HEX(DATABASE()), @@server_uuid;\n"
            + sql.rstrip()
            + "\n"
        )
        try:
            result = subprocess.run(
                self.command(),
                input=payload,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired as error:
            raise MySqlExecutionError(
                f"mysql execution exceeded {timeout_seconds} seconds"
            ) from error
        if result.returncode != 0:
            raise mysql_error(result.stderr or result.stdout, result.returncode)
        lines = result.stdout.splitlines()
        if not lines:
            raise MigrationError("mysql did not report its database identity")
        self._validate_identity_line(lines[0])
        return "\n".join(lines[1:]).strip()

    def _session_execute(self, sql: str, *, timeout_seconds: int) -> str:
        process = self.lock_process
        lines = self._lines
        if process is None or process.stdin is None or lines is None:
            raise MigrationError("mysql migration session is not open")
        if process.poll() is not None:
            raise MigrationError("database migration lock session was lost")
        token = "__NUONO_SQL_" + secrets.token_hex(16) + "__"
        try:
            process.stdin.write(sql.rstrip() + "\n")
            process.stdin.write(f"SELECT {sql_literal(token)};\n")
            process.stdin.flush()
        except BrokenPipeError as error:
            self._terminate_session()
            raise MigrationError("database migration lock session was lost") from error

        values: list[str] = []
        deadline = time.monotonic() + timeout_seconds
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                self._terminate_session()
                raise MySqlExecutionError(
                    f"mysql execution exceeded {timeout_seconds} seconds"
                )
            try:
                line = lines.get(timeout=remaining)
            except queue.Empty as error:
                self._terminate_session()
                raise MySqlExecutionError(
                    f"mysql execution exceeded {timeout_seconds} seconds"
                ) from error
            if line is None:
                self._close_session_handles()
                raise mysql_error("\n".join(values), process.returncode)
            if line == token:
                return "\n".join(values).strip()
            if line:
                values.append(line)

    def _session_preamble(self) -> str:
        return (
            "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;\n"
            "SET SESSION lock_wait_timeout = 5;\n"
            "SET SESSION innodb_lock_wait_timeout = 5;\n"
        )

    def _validate_identity_line(self, line: str) -> None:
        self.server_uuid = validate_identity(
            line,
            self.expected_schema,
            self.server_uuid,
        )

    def _terminate_session(self) -> None:
        process = self.lock_process
        if process is not None and process.poll() is None:
            process.kill()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                pass
        self._close_session_handles()

    def _close_session_handles(self) -> None:
        process = self.lock_process
        self.lock_process = None
        if process is not None:
            for stream in (process.stdin, process.stdout):
                if stream is not None and not stream.closed:
                    stream.close()
        reader = self._reader
        self._reader = None
        self._lines = None
        if reader is not None and reader is not threading.current_thread():
            reader.join(timeout=1)
