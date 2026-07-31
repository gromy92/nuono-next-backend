from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path, PurePosixPath

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import (  # noqa: E402
    load_catalog,
    sha256_bytes,
)
from schema_migrations.core import (  # noqa: E402
    Migration,
    MigrationError,
    MigrationRunner,
    plan_migrations,
)
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402
from schema_migrations.mysql_support import MySqlExecutionError  # noqa: E402


INTEGRITY_MIGRATION_KEY = "215_procurement_fulfillment_balance_quantity_invariant.sql"

@unittest.skipUnless(
    os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
    "requires an isolated MySQL schema",
)
class ReleaseSchemaMigrationsMySqlTest(unittest.TestCase):
    def test_apply_idempotency_lock_drift_and_repair_forward(self):
        defaults_file = Path(os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"])
        expected_schema = os.environ.get("NUONO_MIGRATION_EXPECTED_SCHEMA",
                                         "nuono_schema_migration_ci")
        resources = SCRIPT_DIR.parent / "src/main/resources"
        migrations = load_catalog(resources)
        database = MySqlMigrationDatabase(
            defaults_file,
            expected_schema=expected_schema,
            expected_host="127.0.0.1",
            expected_port=3306,
        )
        self.addCleanup(database.close)
        runner = MigrationRunner(
            database,
            migrations,
            release_commit="a" * 40,
            installed_by="ci-mysql",
            lock_timeout_seconds=5,
        )

        self.assertEqual({}, database.load_states())
        self.prepare_fulfillment_balance_fixture(database)
        integrity = next(item for item in migrations if item.key == INTEGRITY_MIGRATION_KEY)
        approvals = [integrity.key]
        with self.assertRaisesRegex(MigrationError, "missing " + integrity.key):
            runner.apply()
        self.assertNotIn(integrity.key, database.load_states())
        with self.assertRaisesRegex(MigrationError, integrity.key):
            runner.apply(approved_managed=approvals)
        states = database.load_states()
        self.assertEqual("FAILED", states[integrity.key].state)
        self.assertEqual(
            "2",
            database.client.execute(
                "SELECT COUNT(*) FROM procurement_fulfillment_balance "
                "WHERE planned_quantity < 0 "
                "OR confirmed_quantity < 0 "
                "OR abnormal_quantity < 0 "
                "OR reserved_quantity < 0 "
                "OR logistics_handoff_quantity < 0 "
                "OR available_quantity < 0 "
                "OR confirmed_quantity <> abnormal_quantity "
                "+ reserved_quantity + logistics_handoff_quantity "
                "+ available_quantity;"
            ),
        )
        self.assertEqual(
            "0",
            database.client.execute(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                "WHERE constraint_schema=DATABASE() "
                "AND constraint_name LIKE 'chk_fulfillment_balance_%';"
            ),
        )
        database.client.execute(
            "DELETE FROM procurement_fulfillment_balance;"
            "INSERT INTO procurement_fulfillment_balance "
            "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
            "reserved_quantity, logistics_handoff_quantity, available_quantity) "
            "VALUES (10, 10, 8, 1, 1, 1, 5);"
        )
        database.client.execute(
            "ALTER TABLE procurement_fulfillment_balance ADD CONSTRAINT "
            "chk_fulfillment_balance_planned_nonnegative "
            "CHECK (planned_quantity >= -1) ENFORCED;"
        )
        with self.assertRaisesRegex(MigrationError, integrity.key):
            runner.repair_forward(
                integrity.key, rerun=True, approved_managed=approvals
            )
        database.client.execute(
            "ALTER TABLE procurement_fulfillment_balance DROP CHECK "
            "chk_fulfillment_balance_planned_nonnegative;"
        )
        repair_result = runner.repair_forward(
            integrity.key, rerun=True, approved_managed=approvals
        )
        self.assertEqual("RERUN_APPLIED", repair_result)
        self.assertEqual([], runner.apply(approved_managed=approvals))
        self.assertTrue(all(state.state == "APPLIED"
                            for state in database.load_states().values()))
        self.assertTrue(database.postcheck(integrity))
        database.run_script(integrity)
        database.run_script(integrity)
        self.assertTrue(database.postcheck(integrity))
        database.client.execute(
            "INSERT INTO procurement_fulfillment_balance "
            "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
            "reserved_quantity, logistics_handoff_quantity, available_quantity) "
            "VALUES (11, 10, 10, 2, 3, 1, 4);"
        )
        self.assert_mysql_check_rejects(
            database,
            "INSERT INTO procurement_fulfillment_balance "
            "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
            "reserved_quantity, logistics_handoff_quantity, available_quantity) "
            "VALUES (12, 10, 5, 0, -1, 0, 6);",
        )
        self.assert_mysql_check_rejects(
            database,
            "INSERT INTO procurement_fulfillment_balance "
            "(id, planned_quantity, confirmed_quantity, abnormal_quantity, "
            "reserved_quantity, logistics_handoff_quantity, available_quantity) "
            "VALUES (13, 10, 8, 1, 1, 1, 6);",
        )
        self.assertEqual(
            "2",
            database.client.execute(
                "SELECT COUNT(*) FROM procurement_fulfillment_balance;"
            ),
        )
        database.client.execute(
            "ALTER TABLE nuono_schema_migration "
            "MODIFY COLUMN gmt_updated DATETIME(6) NOT NULL "
            "DEFAULT CURRENT_TIMESTAMP(6);"
        )
        self.assertFalse(database.postcheck(migrations[0]))
        database.client.execute(
            "ALTER TABLE nuono_schema_migration "
            "MODIFY COLUMN gmt_updated DATETIME(6) NOT NULL "
            "DEFAULT CURRENT_TIMESTAMP(6) "
            "ON UPDATE CURRENT_TIMESTAMP(6);"
        )
        self.assertTrue(database.postcheck(migrations[0]))
        first = MySqlMigrationDatabase(
            defaults_file,
            expected_schema=expected_schema,
            expected_host="127.0.0.1",
            expected_port=3306,
        )
        second = MySqlMigrationDatabase(
            defaults_file,
            expected_schema=expected_schema,
            expected_host="127.0.0.1",
            expected_port=3306,
        )
        self.addCleanup(first.close)
        self.addCleanup(second.close)
        first.acquire_lock(1)
        try:
            with self.assertRaisesRegex(MigrationError, "not acquired"):
                second.acquire_lock(0)
        finally:
            second.release_lock()
            first.release_lock()

        # Production already executed 223 before the forward catalog existed.
        # Recreate that exact boundary: 223 is present in the schema, both
        # history tables are absent, and the new 227+ catalog must bootstrap
        # without trying to back-insert or undo the published migration.
        database.client.execute(
            "CREATE TABLE IF NOT EXISTS product_site_offer ("
            "id BIGINT NOT NULL, logical_store_id BIGINT NOT NULL, "
            "site_id BIGINT NOT NULL, maintenance_enabled BIT(1) NOT NULL, "
            "is_active BIT(1) NOT NULL, PRIMARY KEY (id));"
        )
        database.client.execute(
            (resources / "db/init/223_product_site_offer_active_state_evidence.sql")
            .read_text(encoding="utf-8")
        )
        database.client.execute(
            "DROP TABLE nuono_schema_migration_attempt;"
            "DROP TABLE nuono_schema_migration;"
        )
        self.assertEqual({}, database.load_states())
        self.assertEqual(
            [migration.key for migration in migrations[1:]],
            runner.apply(
                approved_managed=[
                    migration.key
                    for migration in migrations
                    if migration.kind == "MANAGED"
                ]
            ),
        )
        self.assertEqual(
            "2",
            database.client.execute(
                "SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema=DATABASE() "
                "AND table_name='product_site_offer' "
                "AND column_name IN ('active_state_source', "
                "'active_state_synced_at');"
            ),
        )
        self.assertEqual(
            "1",
            database.client.execute(
                "SELECT COUNT(*) FROM information_schema.statistics "
                "WHERE table_schema=DATABASE() "
                "AND table_name='product_site_offer' "
                "AND index_name='idx_product_site_offer_replenishment_coverage' "
                "AND seq_in_index=1;"
            ),
        )
        self.assertEqual(
            {migration.key for migration in migrations},
            set(database.load_states()),
        )

        target = integrity
        database.client.execute(
            "UPDATE nuono_schema_migration h "
            "JOIN nuono_schema_migration_attempt a "
            "ON a.migration_key=h.migration_key "
            "AND a.attempt_no=h.attempt_no "
            "SET h.state='FAILED', a.state='FAILED' "
            f"WHERE h.migration_key='{target.key}';"
        )
        self.assertEqual(
            "POSTCHECK_RECONCILED",
            runner.repair_forward(
                target.key,
                approved_managed=[target.key],
            ),
        )

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            failure_order = migrations[-1].order + 1
            blocked_order = failure_order + 1
            failure_key = f"{failure_order:03d}_failure_probe.sql"
            blocked_key = f"{blocked_order:03d}_must_not_run.sql"
            failing = self.migration(
                root,
                failure_order,
                failure_key,
                "CREATE TABLE migration_failure_before (id INT);\n"
                "THIS IS NOT VALID SQL;\n"
                "CREATE TABLE migration_failure_after (id INT);\n",
                "SELECT 0;\n",
            )
            blocked = self.migration(
                root,
                blocked_order,
                blocked_key,
                "CREATE TABLE migration_must_not_run (id INT);\n",
                "SELECT 1;\n",
            )
            extended = (*migrations, failing, blocked)
            failing_runner = MigrationRunner(
                database,
                extended,
                release_commit="a" * 40,
                installed_by="ci-mysql",
            )
            with self.assertRaisesRegex(MigrationError, failure_key):
                failing_runner.apply()
            states = database.load_states()
            self.assertEqual("FAILED", states[failing.key].state)
            self.assertNotIn(blocked.key, states)
            self.assertEqual(
                "0",
                database.client.execute(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    "WHERE table_schema=DATABASE() "
                    "AND table_name IN "
                    "('migration_failure_after', 'migration_must_not_run');"
                ),
            )

        database.client.execute(
            "UPDATE nuono_schema_migration h "
            "JOIN nuono_schema_migration_attempt a "
            "ON a.migration_key=h.migration_key "
            "AND a.attempt_no=h.attempt_no "
            "SET h.checksum_sha256=REPEAT('0', 64), "
            "a.checksum_sha256=REPEAT('0', 64) "
            f"WHERE h.migration_key='{target.key}';"
        )
        with self.assertRaisesRegex(MigrationError, "checksum drift"):
            plan_migrations(extended, database.load_states())

    @staticmethod
    def prepare_fulfillment_balance_fixture(database):
        database.client.execute(
            "CREATE TABLE procurement_fulfillment_balance ("
            "id BIGINT NOT NULL,"
            "planned_quantity INT NOT NULL DEFAULT 0,"
            "confirmed_quantity INT NOT NULL DEFAULT 0,"
            "abnormal_quantity INT NOT NULL DEFAULT 0,"
            "reserved_quantity INT NOT NULL DEFAULT 0,"
            "logistics_handoff_quantity INT NOT NULL DEFAULT 0,"
            "available_quantity INT NOT NULL DEFAULT 0,"
            "PRIMARY KEY (id)"
            ") ENGINE=InnoDB;"
            "INSERT INTO procurement_fulfillment_balance VALUES "
            "(1, 10, 5, 0, -1, 0, 6),"
            "(2, 10, 8, 1, 1, 1, 6);"
        )

    def assert_mysql_check_rejects(self, database, sql):
        with self.assertRaises(MySqlExecutionError) as caught:
            database.client.execute(sql)
        self.assertEqual(3819, caught.exception.error_code)

    @staticmethod
    def migration(root, order, key, script, postcheck):
        script_file = root / key
        postcheck_file = root / ("postcheck_" + key)
        script_file.write_text(script, encoding="utf-8")
        postcheck_file.write_text(postcheck, encoding="utf-8")
        return Migration(
            order,
            key,
            "AUTO_ADDITIVE",
            PurePosixPath("db/init") / key,
            PurePosixPath("db/postcheck") / key,
            sha256_bytes(script.encode("utf-8")),
            sha256_bytes(postcheck.encode("utf-8")),
            script.encode("utf-8"),
            postcheck.encode("utf-8"),
            script_file,
            postcheck_file,
        )


if __name__ == "__main__":
    unittest.main()
