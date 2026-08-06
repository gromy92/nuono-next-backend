from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path


SCRIPT_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPT_ROOT.parent
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from ci.dp_pull_runtime_successor_fixture import prepare_successor_fixture  # noqa: E402
from ci.dp_pull_runtime_successor_scenario import drop_successor_objects  # noqa: E402
from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.core import (  # noqa: E402
    MigrationError,
    MigrationRunner,
    MigrationState,
    plan_migrations,
)
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402


RUNTIME_TABLES = (
    "dp_pull_task",
    "dp_pull_runtime_leader",
    "dp_pull_backoff_hold",
    "dp_pull_emergency_claim_hold",
    "dp_pull_schedule_cutover",
    "dp_pull_scope_admission",
    "dp_pull_scope_binding_epoch",
    "dp_pull_schedule_anchor",
    "dp_pull_scope_progress",
    "dp_pull_snapshot_stage",
    "dp_pull_snapshot_stage_page",
    "dp_pull_snapshot_stage_item",
    "dp_pull_snapshot_apply",
    "dp_pull_dp10_stage_page",
    "dp_pull_dp10_stage_item",
    "dp_pull_dp10_stage_fingerprint_count",
    "dp_pull_dp10_stage_identity",
    "dp_pull_dp10_stage_cleanup",
    "dp_pull_report_artifact",
    "dp_pull_report_download_locator",
    "dp_pull_report_apply",
)
DP_ORDERS = frozenset({243, 244, 245, 246, 247, 248, 249})
FAILED_MIGRATION = "245_dp_pull_snapshot_bounded_apply.sql"
VIEW_MARKER = (
    "CREATE OR REPLACE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW "
    "`official_warehouse_current_inventory_snapshot_line_raw`"
)


class RealMySqlScenarioDatabase:
    """MigrationRunner state machine backed by real MySQL schema operations."""

    def __init__(self, mysql, *, fail_245_before_view: bool):
        self.mysql = mysql
        self.states: dict[str, MigrationState] = {}
        self.fail_245_before_view = fail_245_before_view

    def acquire_lock(self, timeout_seconds):
        self.mysql.acquire_lock(timeout_seconds)

    def release_lock(self):
        self.mysql.release_lock()

    def bootstrap(self, migration, release_commit, installed_by):
        if migration.key not in self.states:
            self.states[migration.key] = self._state(migration, "APPLIED", 1)

    def load_states(self):
        return dict(self.states)

    def begin(self, migration, release_commit, installed_by, operation):
        previous = self.states.get(migration.key)
        attempt = 1 if previous is None else previous.attempt_no + 1
        self.states[migration.key] = self._state(migration, "APPLYING", attempt)
        return attempt

    def run_script(self, migration):
        if migration.key == FAILED_MIGRATION and self.fail_245_before_view:
            self.fail_245_before_view = False
            prefix, marker, _ = migration.script_sql.partition(VIEW_MARKER)
            if not marker:
                raise AssertionError("245 view failure marker missing")
            self.mysql.client.execute(prefix)
            raise MigrationError("synthetic CREATE VIEW privilege failure")
        self.mysql.client.execute(migration.script_sql)

    def postcheck(self, migration):
        return self._check(migration, migration.postcheck_sql)

    def livecheck(self, migration):
        return self._check(migration, migration.livecheck_sql)

    def acknowledge_runtime_drain(self, migration_key):
        raise AssertionError(f"unexpected runtime drain: {migration_key}")

    def mark_applied(self, migration, attempt_no):
        self.states[migration.key] = self._state(migration, "APPLIED", attempt_no)

    def mark_failed(self, migration, attempt_no, error):
        self.states[migration.key] = self._state(migration, "FAILED", attempt_no)

    def reconcile(self, migration, blocked_attempt_no, release_commit, installed_by):
        attempt = blocked_attempt_no + 1
        self.states[migration.key] = self._state(migration, "APPLIED", attempt)
        return attempt

    def _check(self, migration, sql):
        if migration.kind == "BOOTSTRAP":
            return True
        return self.mysql.client.execute_readonly(sql).splitlines() == ["1"]

    @staticmethod
    def _state(migration, state, attempt):
        return MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            state,
            attempt,
        )


@unittest.skipUnless(
    os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
    "requires an isolated MySQL schema",
)
class DpPullRuntimeMigrationRunnerMySqlTest(unittest.TestCase):
    def test_runner_revalidates_completed_prefix_and_repairs_245_middle_state(self):
        mysql = self._database()
        self.addCleanup(mysql.close)
        resources = REPOSITORY_ROOT / "src/main/resources"
        migrations = self._scenario_catalog(resources)

        self._prepare(mysql, resources / "db")
        clean = RealMySqlScenarioDatabase(mysql, fail_245_before_view=False)
        clean_runner = self._runner(clean, migrations)
        self.assertEqual(
            [migration.key for migration in migrations[1:]],
            clean_runner.apply(),
        )
        self._assert_current(clean_runner, clean, migrations)

        self._prepare(mysql, resources / "db")
        failed = RealMySqlScenarioDatabase(mysql, fail_245_before_view=True)
        failed_runner = self._runner(failed, migrations)
        with self.assertRaisesRegex(MigrationError, FAILED_MIGRATION):
            failed_runner.apply()
        self.assertEqual("FAILED", failed.load_states()[FAILED_MIGRATION].state)
        self.assertEqual(
            "RERUN_APPLIED",
            failed_runner.repair_forward(FAILED_MIGRATION, rerun=True),
        )
        self.assertEqual(
            [migration.key for migration in migrations if migration.order >= 246],
            failed_runner.apply(),
        )
        self._assert_current(failed_runner, failed, migrations)

    def _database(self):
        return MySqlMigrationDatabase(
            Path(os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"]),
            expected_schema=os.environ.get(
                "NUONO_MIGRATION_EXPECTED_SCHEMA", "nuono_schema_migration_ci"
            ),
            expected_host="127.0.0.1",
            expected_port=int(os.environ.get("NUONO_MIGRATION_EXPECTED_PORT", "3306")),
        )

    def _prepare(self, mysql, db_resources):
        drop_successor_objects(mysql)
        mysql.client.execute(
            "SET FOREIGN_KEY_CHECKS=0;"
            + "".join(f"DROP TABLE IF EXISTS `{table}`;" for table in reversed(RUNTIME_TABLES))
            + "SET FOREIGN_KEY_CHECKS=1;"
        )
        self._ensure_sequence(mysql)
        fixture = prepare_successor_fixture(mysql, db_resources)
        self.addCleanup(fixture.cleanup, mysql)
        self._ensure_asn_line(mysql)
        mysql.client.execute(
            "ALTER TABLE official_warehouse_asn_line "
            "DROP COLUMN IF EXISTS manual_quantity;"
        )

    @staticmethod
    def _ensure_sequence(mysql):
        mysql.client.execute(
            "CREATE TABLE IF NOT EXISTS noon_pull_id_sequence ("
            "sequence_name VARCHAR(100) NOT NULL,next_id BIGINT NOT NULL,"
            "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
            "gmt_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP "
            "ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(sequence_name)) "
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;"
        )

    @staticmethod
    def _ensure_asn_line(mysql):
        mysql.client.execute(
            "CREATE TABLE IF NOT EXISTS official_warehouse_asn_line ("
            "id BIGINT NOT NULL,qty INT DEFAULT NULL,PRIMARY KEY(id)) "
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;"
        )

    @staticmethod
    def _scenario_catalog(resources):
        catalog = load_catalog(resources)
        return tuple(
            migration
            for migration in catalog
            if migration.kind == "BOOTSTRAP" or migration.order in DP_ORDERS
        )

    @staticmethod
    def _runner(database, migrations):
        return MigrationRunner(
            database,
            migrations,
            release_commit="a" * 40,
            installed_by="dp-successor-ci",
            lock_timeout_seconds=5,
        )

    def _assert_current(self, runner, database, migrations):
        self.assertEqual([], plan_migrations(migrations, database.load_states()))
        self.assertTrue(all(database.livecheck(migration) for migration in migrations))
        self.assertEqual([], runner.apply())


if __name__ == "__main__":
    unittest.main()
