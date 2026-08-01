from __future__ import annotations

from typing import Mapping, Sequence

from schema_migrations.model import (
    BLOCKED_STATES,
    Migration,
    MigrationError,
    MigrationState,
)
from schema_migrations.mysql_client import MySqlClient
from schema_migrations.mysql_history_sql import (
    ATTEMPT_TABLE,
    HISTORY_TABLE,
    begin_statements,
    bootstrap_statements,
    finish_sql,
    reconcile_statements,
)


class MySqlMigrationHistory:
    def __init__(self, client: MySqlClient):
        self.client = client

    def table_count(self) -> int:
        output = self.client.execute_readonly(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema=DATABASE() AND table_name IN "
            f"('{HISTORY_TABLE}', '{ATTEMPT_TABLE}');\n"
        )
        try:
            return int(output)
        except ValueError as error:
            raise MigrationError(
                f"invalid migration history table count: {output!r}"
            ) from error

    def load_states(self) -> Mapping[str, MigrationState]:
        table_count = self.table_count()
        if table_count == 0:
            return {}
        if table_count != 2:
            raise MigrationError("migration history tables are partial")
        output = self.client.execute_readonly(
            "SELECT h.migration_key, h.checksum_sha256, h.postcheck_sha256, "
            "h.state, h.attempt_no, "
            "a.checksum_sha256, a.postcheck_sha256, a.state, a.attempt_no "
            f"FROM {HISTORY_TABLE} h LEFT JOIN {ATTEMPT_TABLE} a "
            "ON a.migration_key=h.migration_key AND a.attempt_no=h.attempt_no "
            "ORDER BY h.migration_key;\n"
        )
        states = {}
        for line in output.splitlines():
            columns = line.split("\t")
            if len(columns) != 9:
                raise MigrationError(f"invalid migration history row: {line!r}")
            key, checksum, postcheck, state, raw_attempt = columns[:5]
            attempt_checksum, attempt_postcheck, attempt_state, raw_joined = columns[5:]
            try:
                attempt_no = int(raw_attempt)
                joined_attempt = int(raw_joined)
            except ValueError as error:
                raise MigrationError(
                    f"invalid migration attempt number: {line!r}"
                ) from error
            if (
                attempt_no != joined_attempt
                or checksum != attempt_checksum
                or postcheck != attempt_postcheck
                or state != attempt_state
            ):
                raise MigrationError(
                    f"{key}: current history and attempt rows are inconsistent"
                )
            if key in states:
                raise MigrationError(f"duplicate migration history key: {key}")
            states[key] = MigrationState(
                key, checksum, postcheck, state, attempt_no
            )
        orphan_count = self.client.execute_readonly(
            f"SELECT COUNT(*) FROM {ATTEMPT_TABLE} a "
            f"LEFT JOIN {HISTORY_TABLE} h ON h.migration_key=a.migration_key "
            "WHERE h.migration_key IS NULL;\n"
        )
        if orphan_count != "0":
            raise MigrationError("migration attempt history contains orphan rows")
        return states

    def record_bootstrap(
        self,
        migration: Migration,
        state: str,
        release_commit: str,
        installed_by: str,
    ) -> None:
        self._transaction(
            bootstrap_statements(
                migration, state, release_commit, installed_by
            )
        )

    def begin(
        self,
        migration: Migration,
        release_commit: str,
        installed_by: str,
        operation: str,
    ) -> int:
        if operation not in {"APPLY", "RERUN"}:
            raise MigrationError(f"unsupported migration operation: {operation}")
        existing = self.load_states().get(migration.key)
        if existing is None:
            if operation != "APPLY":
                raise MigrationError(f"{migration.key}: RERUN has no failed attempt")
            attempt_no = 1
        else:
            if existing.state != "FAILED":
                raise MigrationError(
                    f"{migration.key}: cannot begin from {existing.state}"
                )
            if operation != "RERUN":
                raise MigrationError(
                    f"{migration.key}: failed history requires RERUN"
                )
            self._assert_checksums(migration, existing)
            attempt_no = existing.attempt_no + 1
        self._transaction(
            begin_statements(
                migration,
                release_commit,
                installed_by,
                operation,
                attempt_no,
                existing,
            )
        )
        return attempt_no

    def finish(
        self,
        migration: Migration,
        attempt_no: int,
        state: str,
        error_code: str | None,
        error_digest: str | None,
        error_summary: str | None,
    ) -> None:
        sql = finish_sql(
            migration,
            attempt_no,
            state,
            error_code,
            error_digest,
            error_summary,
        )
        output = (
            self.client.execute(sql)
            if self.client.lock_process is not None
            else self.client.execute_recovery(sql)
        )
        if output.splitlines()[-1:] != ["2"]:
            raise MigrationError(
                f"{migration.key}: atomic history transition to {state} was rejected"
            )

    def reconcile(
        self,
        migration: Migration,
        blocked_attempt_no: int,
        release_commit: str,
        installed_by: str,
    ) -> int:
        existing = self.load_states().get(migration.key)
        if (
            existing is None
            or existing.state not in BLOCKED_STATES
            or existing.attempt_no != blocked_attempt_no
        ):
            raise MigrationError(
                f"{migration.key}: blocked attempt changed before reconciliation"
            )
        self._assert_checksums(migration, existing)
        self._transaction(
            reconcile_statements(
                migration,
                blocked_attempt_no,
                release_commit,
                installed_by,
            )
        )
        return blocked_attempt_no + 1

    def _transaction(self, steps: Sequence[tuple[str, int]]) -> None:
        self.client.execute("START TRANSACTION;\n")
        try:
            for sql, expected_rows in steps:
                output = self.client.execute(sql + "\nSELECT ROW_COUNT();\n")
                if output.splitlines()[-1:] != [str(expected_rows)]:
                    raise MigrationError(
                        "migration history transaction changed an unexpected row count"
                    )
            self.client.execute("COMMIT;\n")
        except Exception:
            try:
                if self.client.lock_process is not None:
                    self.client.execute("ROLLBACK;\n", timeout_seconds=10)
            except Exception:
                pass
            raise

    @staticmethod
    def _assert_checksums(
        migration: Migration,
        state: MigrationState,
    ) -> None:
        if (
            state.checksum != migration.checksum
            or state.postcheck_checksum != migration.postcheck_checksum
        ):
            raise MigrationError(f"{migration.key}: history checksum drift")
