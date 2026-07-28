from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Mapping

from schema_migrations.model import Migration, MigrationError, MigrationState
from schema_migrations.mysql_client import MySqlClient
from schema_migrations.mysql_history import MySqlMigrationHistory
from schema_migrations.mysql_support import (
    MySqlExecutionError,
    safe_error_summary,
)

LOCK_NAME = "nuono:schema-migrations"


class MySqlMigrationDatabase:
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
        self.client = MySqlClient(
            defaults_file,
            expected_schema=expected_schema,
            expected_host=expected_host,
            expected_port=expected_port,
            mysql_bin=mysql_bin,
            execution_timeout_seconds=execution_timeout_seconds,
        )
        self.history = MySqlMigrationHistory(self.client)

    def acquire_lock(self, timeout_seconds: int) -> None:
        self.client.acquire_lock(LOCK_NAME, timeout_seconds)

    def release_lock(self) -> None:
        self.client.release_lock(LOCK_NAME)

    def close(self) -> None:
        self.client.close()

    def bootstrap(
        self,
        migration: Migration,
        release_commit: str,
        installed_by: str,
    ) -> None:
        table_count = self.history.table_count()
        if table_count == 1:
            raise MigrationError(
                "migration history tables are partial; repair them before continuing"
            )
        if table_count == 0:
            self.run_script(migration)
            if self.history.table_count() != 2 or not self.postcheck(migration):
                raise MigrationError("migration history bootstrap postcheck failed")
            state = "APPLIED"
        else:
            if not self.postcheck(migration):
                raise MigrationError("migration history schema is incompatible")
            states = self.load_states()
            if migration.key in states:
                return
            if states:
                raise MigrationError(
                    "migration history is missing its BOOTSTRAP record"
                )
            state = "BASELINED"
        self.history.record_bootstrap(
            migration,
            state,
            release_commit,
            installed_by,
        )

    def load_states(self) -> Mapping[str, MigrationState]:
        return self.history.load_states()

    def begin(
        self,
        migration: Migration,
        release_commit: str,
        installed_by: str,
        operation: str,
    ) -> int:
        return self.history.begin(
            migration,
            release_commit,
            installed_by,
            operation,
        )

    def run_script(self, migration: Migration) -> None:
        self.client.execute(migration.script_sql)

    def postcheck(self, migration: Migration) -> bool:
        try:
            output = self.client.execute_readonly(migration.postcheck_sql)
        except MySqlExecutionError as error:
            if error.error_code == 1146:
                return False
            raise
        return output.splitlines() == ["1"]

    def mark_applied(self, migration: Migration, attempt_no: int) -> None:
        self.history.finish(migration, attempt_no, "APPLIED", None, None, None)

    def mark_failed(
        self,
        migration: Migration,
        attempt_no: int,
        error: Exception,
    ) -> None:
        summary = safe_error_summary(error)
        digest = hashlib.sha256(summary.encode("utf-8")).hexdigest()
        if isinstance(error, MySqlExecutionError) and error.error_code is not None:
            code = f"MYSQL_{error.error_code}"
        else:
            code = type(error).__name__[:64]
        self.history.finish(
            migration,
            attempt_no,
            "FAILED",
            code,
            digest,
            summary,
        )

    def reconcile(
        self,
        migration: Migration,
        blocked_attempt_no: int,
        release_commit: str,
        installed_by: str,
    ) -> int:
        return self.history.reconcile(
            migration,
            blocked_attempt_no,
            release_commit,
            installed_by,
        )
