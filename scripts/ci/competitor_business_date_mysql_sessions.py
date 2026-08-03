"""Dedicated MySQL sessions used by lock-conflict integration checks."""
from __future__ import annotations

import os
import re
import subprocess
import tempfile
from typing import Any

from competitor_business_date.mysql_cli import MysqlCli
from competitor_business_date.persistent_fence import FENCE_NAME, FENCE_TABLE


class SharedFenceLock:
    """Hold the same shared row lock acquired by a backend request."""

    def __init__(self, mysql: MysqlCli):
        self.mysql = mysql
        self.process: subprocess.Popen[str] | None = None
        self.errors: Any = None
        self.connection_id: int | None = None

    def __enter__(self) -> "SharedFenceLock":
        self.errors = tempfile.TemporaryFile(mode="w+t", encoding="utf-8")
        self.process = subprocess.Popen(
            [*self.mysql.command, "--unbuffered"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=self.errors,
            text=True,
            env={**os.environ, "MYSQL_HISTFILE": os.devnull},
        )
        assert self.process.stdin is not None
        assert self.process.stdout is not None
        self.process.stdin.write(
            "SET SESSION innodb_lock_wait_timeout = 5;\n"
            "START TRANSACTION;\n"
            "SELECT CONCAT('LOCKED|', CONNECTION_ID()) "
            f"FROM `{FENCE_TABLE}` WHERE `fence_name` = '{FENCE_NAME}' "
            "FOR SHARE;\n"
            "SELECT SLEEP(2147483);\n"
        )
        self.process.stdin.close()
        response = self.process.stdout.readline().strip()
        match = re.fullmatch(r"LOCKED\|(\d+)", response)
        if match is None:
            detail = self._error_detail()
            self.close()
            raise RuntimeError(
                f"shared fence lock was not acquired: {response or detail}"
            )
        self.connection_id = int(match.group(1))
        return self

    def assert_held(self) -> None:
        if self.process is None or self.process.poll() is not None:
            raise RuntimeError("shared fence lock session was lost")

    def close(self) -> None:
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
        if self.errors is not None:
            self.errors.close()
        self.process = None
        self.errors = None

    def _error_detail(self) -> str:
        if self.errors is None:
            return "no stderr"
        self.errors.seek(0)
        return " ".join(self.errors.read().strip().split())[-1000:]

    def __exit__(self, *_: object) -> None:
        self.close()
