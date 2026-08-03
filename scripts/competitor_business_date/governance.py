"""Release lock, advisory lock, and writer-fence assertions."""
from __future__ import annotations

import fcntl
import os
import re
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Any

from .mysql_cli import MysqlCli, MysqlCliError


ADVISORY_LOCK_NAME = "nuono:competitor-business-date-correction:v1"


class GovernanceError(RuntimeError):
    pass


class ReleaseFileLock:
    def __init__(self, path: Path):
        self.path = Path(path)
        self.handle: Any = None

    def __enter__(self) -> "ReleaseFileLock":
        if self.path.is_symlink():
            raise GovernanceError("release lock file must not be a symbolic link")
        self.path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        flags = os.O_CREAT | os.O_RDWR
        if hasattr(os, "O_NOFOLLOW"):
            flags |= os.O_NOFOLLOW
        descriptor = os.open(self.path, flags, 0o600)
        self.handle = os.fdopen(descriptor, "a+")
        try:
            fcntl.flock(self.handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            self.handle.close()
            self.handle = None
            raise GovernanceError("shared production release lock is already held") from error
        return self

    def __exit__(self, *_: object) -> None:
        if self.handle is not None:
            fcntl.flock(self.handle, fcntl.LOCK_UN)
            self.handle.close()
            self.handle = None


class MysqlAdvisoryLock:
    """Hold a connection-scoped MySQL lock in a dedicated sleeping session."""

    def __init__(self, mysql: MysqlCli):
        self.mysql = mysql
        self.process: subprocess.Popen[str] | None = None
        self.errors: Any = None
        self.connection_id: int | None = None
        self._last_database_check = 0.0

    def __enter__(self) -> "MysqlAdvisoryLock":
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
        lock_hex = ADVISORY_LOCK_NAME.encode("utf-8").hex()
        self.process.stdin.write(
            "SELECT CONCAT("
            f"IF(GET_LOCK(CONVERT(X'{lock_hex}' USING utf8mb4), 0)=1,"
            "'LOCKED','BUSY'),'|',CONNECTION_ID());\n"
            "SELECT SLEEP(2147483);\n"
        )
        self.process.stdin.close()
        response = self.process.stdout.readline().strip()
        match = re.fullmatch(r"LOCKED\|(\d+)", response)
        if match is None:
            detail = self._error_detail()
            self.close()
            raise GovernanceError(
                f"database advisory lock was not acquired: {response or detail}"
            )
        self.connection_id = int(match.group(1))
        return self

    def assert_held(self) -> None:
        if self.process is None or self.process.poll() is not None:
            raise GovernanceError("database advisory-lock session was lost")
        now = time.monotonic()
        if now - self._last_database_check < 5:
            return
        lock_hex = ADVISORY_LOCK_NAME.encode("utf-8").hex()
        evidence = self.mysql.query_one_json(
            """
SELECT REPLACE(TO_BASE64(CAST(JSON_OBJECT(
  'connection_id', IS_USED_LOCK(
    CONVERT(X'"""
            + lock_hex
            + """' USING utf8mb4)
  )
) AS CHAR CHARACTER SET utf8mb4)), '\\n', '');
"""
        )
        if int(evidence.get("connection_id") or 0) != self.connection_id:
            raise GovernanceError("database advisory lock is no longer owned")
        self._last_database_check = now

    def _error_detail(self) -> str:
        if self.errors is None:
            return "no stderr"
        self.errors.seek(0)
        return " ".join(self.errors.read().strip().split())[-1000:]

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

    def __exit__(self, *_: object) -> None:
        self.close()


def assert_no_backend_jvm(process_listing: str | None = None) -> None:
    if process_listing is None:
        result = subprocess.run(
            ["ps", "-axo", "pid=,command="],
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            raise GovernanceError("unable to inspect backend JVM processes")
        process_listing = result.stdout
    blockers = [
        line.strip()
        for line in process_listing.splitlines()
        if "java" in line.lower()
        and "nuono-next-backend" in line.lower()
        and "grep" not in line.lower()
    ]
    if blockers:
        raise GovernanceError("Nuono backend JVM is still running")


def assert_database_writer_fence(mysql: MysqlCli, holder_connection_id: int) -> None:
    if holder_connection_id <= 0:
        raise GovernanceError("advisory-lock connection ID is invalid")
    sql = _writer_fence_sql(holder_connection_id)
    try:
        evidence = mysql.query_one_json(sql)
    except MysqlCliError as error:
        raise GovernanceError(
            "writer-fence database evidence is unavailable"
        ) from error
    blockers = {
        key: int(value)
        for key, value in evidence.items()
        if key.endswith("_blockers")
    }
    if not blockers or any(blockers.values()):
        raise GovernanceError(f"competitor writer fence has blockers: {blockers}")


def capture_writer_watermark(mysql: MysqlCli) -> dict[str, Any]:
    return mysql.query_one_json(
        """
SELECT REPLACE(TO_BASE64(CAST(JSON_OBJECT(
  'snapshot_max_id', (
    SELECT COALESCE(MAX(id), 0)
    FROM operations_competitor_product_snapshot
  ),
  'snapshot_max_updated', (
    SELECT CAST(MAX(gmt_updated) AS CHAR)
    FROM operations_competitor_product_snapshot
  ),
  'event_max_id', (
    SELECT COALESCE(MAX(id), 0)
    FROM operations_competitor_product_change_event
  ),
  'rank_max_id', (
    SELECT COALESCE(MAX(id), 0)
    FROM operations_competitor_rank_fact
  ),
  'keyword_run_max_id', (
    SELECT COALESCE(MAX(id), 0)
    FROM operations_competitor_keyword_run
  )
) AS CHAR CHARACTER SET utf8mb4)), '\\n', '');
"""
    )


def _writer_fence_sql(holder_connection_id: int) -> str:
    return f"""
SELECT REPLACE(TO_BASE64(CAST(JSON_OBJECT(
  'process_blockers', (
    SELECT COUNT(*) FROM information_schema.processlist
    WHERE id NOT IN (CONNECTION_ID(), {holder_connection_id})
      AND command <> 'Daemon'
      AND LOWER(user) NOT IN ('system user', 'event_scheduler', 'rdsadmin')
  ),
  'transaction_blockers', (
    SELECT COUNT(*)
    FROM information_schema.innodb_trx trx
    LEFT JOIN information_schema.processlist process
      ON process.id = trx.trx_mysql_thread_id
    WHERE trx.trx_mysql_thread_id NOT IN (
      CONNECTION_ID(), {holder_connection_id}
    )
      AND LOWER(COALESCE(process.user, '')) NOT IN (
        'system user', 'event_scheduler', 'rdsadmin'
      )
  ),
  'metadata_lock_blockers', (
    SELECT COUNT(*)
    FROM performance_schema.metadata_locks metadata_lock
    LEFT JOIN performance_schema.threads thread
      ON thread.thread_id = metadata_lock.owner_thread_id
    WHERE metadata_lock.object_schema = DATABASE()
      AND metadata_lock.lock_status = 'GRANTED'
      AND COALESCE(thread.processlist_id, 0) NOT IN (
        CONNECTION_ID(), {holder_connection_id}
      )
  ),
  'task_blockers', (
    SELECT COUNT(*) FROM operational_task
    WHERE is_deleted = b'0'
      AND task_type IN (
        'OPERATIONS_COMPETITOR_REFRESH',
        'OPERATIONS_COMPETITOR_MONITORING',
        'OPERATIONS_COMPETITOR_MONITORING_CYCLE'
      )
      AND status = 'RUNNING'
  ),
  'search_run_blockers', (
    SELECT COUNT(*) FROM operations_competitor_search_run
    WHERE is_deleted = b'0' AND status = 'RUNNING'
  )
) AS CHAR CHARACTER SET utf8mb4)), '\\n', '');
"""
