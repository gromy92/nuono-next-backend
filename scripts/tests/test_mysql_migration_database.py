from __future__ import annotations

import sys
import unittest
from pathlib import Path, PurePosixPath
from unittest.mock import MagicMock, patch

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import sha256_bytes  # noqa: E402
from schema_migrations.core import (  # noqa: E402
    Migration,
    MigrationError,
    MigrationState,
)
from schema_migrations.mysql_support import MySqlExecutionError  # noqa: E402
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402
from schema_migrations.mysql_history import MySqlMigrationHistory  # noqa: E402


class FakeClient:
    def __init__(self, outputs=None):
        self.outputs = list(outputs or [])
        self.executed = []
        self.lock_process = object()

    def execute(self, sql, **kwargs):
        self.executed.append(sql)
        return self.outputs.pop(0) if self.outputs else ""

    def execute_recovery(self, sql, **kwargs):
        self.executed.append(sql)
        return self.outputs.pop(0) if self.outputs else ""

    def execute_readonly(self, sql, **kwargs):
        self.executed.append(sql)
        return self.outputs.pop(0) if self.outputs else ""


class MySqlMigrationDatabaseTest(unittest.TestCase):
    def test_finish_updates_history_and_current_attempt_atomically(self):
        history = MySqlMigrationHistory(FakeClient(["2"]))

        history.finish(self.migration(), 3, "APPLIED", None, None, None)

        sql = history.client.executed[0]
        self.assertIn("UPDATE nuono_schema_migration h", sql)
        self.assertIn("JOIN nuono_schema_migration_attempt a", sql)
        self.assertIn("h.state='APPLYING'", sql)
        self.assertIn("a.state='APPLYING'", sql)
        self.assertIn("SELECT ROW_COUNT()", sql)

    def test_finish_rejects_a_missing_current_attempt_row(self):
        history = MySqlMigrationHistory(FakeClient(["1"]))

        with self.assertRaisesRegex(MigrationError, "atomic history transition"):
            history.finish(self.migration(), 3, "APPLIED", None, None, None)

    def test_partial_history_tables_fail_closed(self):
        history = MySqlMigrationHistory(FakeClient(["1"]))

        with self.assertRaisesRegex(MigrationError, "partial"):
            history.load_states()

    def test_reconcile_appends_a_new_attempt_and_preserves_failed_attempt(self):
        migration = self.migration()
        state = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "FAILED",
            2,
        )
        client = FakeClient(["", "1", "1", ""])
        history = MySqlMigrationHistory(client)

        with patch.object(history, "load_states", return_value={migration.key: state}):
            attempt = history.reconcile(
                migration,
                2,
                "a" * 40,
                "release-operator",
            )

        self.assertEqual(3, attempt)
        combined = "\n".join(client.executed)
        self.assertIn("'RECONCILE'", combined)
        self.assertIn("reconciles_attempt_no", combined)
        self.assertNotIn("UPDATE nuono_schema_migration_attempt", combined)

    def test_missing_table_is_a_false_postcheck_not_a_false_success(self):
        class MissingTableClient(FakeClient):
            def execute_readonly(self, sql, **kwargs):
                raise MySqlExecutionError(
                    "MySQL 1146 (42S02): table is missing",
                    error_code=1146,
                    sqlstate="42S02",
                )

        database = self.database(MissingTableClient())

        self.assertFalse(database.postcheck(self.migration()))

    def test_empty_history_reruns_bootstrap_script_before_baselining(self):
        database = self.database(FakeClient())
        database.history = MagicMock()
        database.history.table_count.return_value = 2
        database.postcheck = MagicMock(return_value=True)
        database.load_states = MagicMock(return_value={})
        database.run_script = MagicMock()
        migration = self.migration()

        database.bootstrap(migration, "a" * 40, "release-operator")

        database.run_script.assert_called_once_with(migration)
        self.assertEqual(2, database.postcheck.call_count)
        database.history.record_bootstrap.assert_called_once_with(
            migration,
            "BASELINED",
            "a" * 40,
            "release-operator",
        )

    def test_empty_history_rejects_failed_bootstrap_baseline_guard(self):
        database = self.database(FakeClient())
        database.history = MagicMock()
        database.history.table_count.return_value = 2
        database.postcheck = MagicMock(return_value=True)
        database.load_states = MagicMock(return_value={})
        database.run_script = MagicMock(
            side_effect=MigrationError("pre-catalog baseline drift")
        )

        with self.assertRaisesRegex(MigrationError, "pre-catalog baseline drift"):
            database.bootstrap(self.migration(), "a" * 40, "release-operator")

        database.history.record_bootstrap.assert_not_called()

    @staticmethod
    def database(client):
        database = object.__new__(MySqlMigrationDatabase)
        database.client = client
        database.history = MySqlMigrationHistory(client)
        return database

    @staticmethod
    def migration():
        script = b"SELECT 1;\n"
        postcheck = b"SELECT 1;\n"
        return Migration(
            order=211,
            key="211_test.sql",
            kind="AUTO_ADDITIVE",
            script_path=PurePosixPath("db/init/211_test.sql"),
            postcheck_path=PurePosixPath("db/postcheck/211_test.sql"),
            livecheck_path=PurePosixPath("db/postcheck/211_test.sql"),
            checksum=sha256_bytes(script),
            postcheck_checksum=sha256_bytes(postcheck),
            livecheck_checksum=sha256_bytes(postcheck),
            script_bytes=script,
            postcheck_bytes=postcheck,
            livecheck_bytes=postcheck,
        )


if __name__ == "__main__":
    unittest.main()
