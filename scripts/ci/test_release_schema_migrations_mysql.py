from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.core import (  # noqa: E402
    MigrationError,
    MigrationRunner,
    plan_migrations,
)
from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402
from ci.release_schema_mysql_scenario import (  # noqa: E402
    build_probe_migration,
    prepare_current_release_fixture,
    verify_applied_schema,
    verify_lock_contention,
)
from ci.release_schema_mysql_official_warehouse_scenario import verify_appointment_concurrency_migration  # noqa: E402
from ci.release_schema_mysql_pre_catalog_scenario import verify_pre_catalog_bootstrap  # noqa: E402
from ci.release_schema_mysql_shipping_batch_scenario import verify_shipping_batch_idempotency_migration  # noqa: E402
from ci.release_schema_mysql_shipping_plan_scenario import verify_shipping_batch_dispatch_plan_uniqueness_migration  # noqa: E402
from ci.release_schema_mysql_forwarder_scenario import (  # noqa: E402
    MIGRATION_KEY as FORWARDER_MIGRATION_KEY,
    prepare_forwarder_fixture,
    verify_forwarder_migration,
)
from ci.release_schema_mysql_forwarder_trigger_scenario import verify_forwarder_trigger_repair  # noqa: E402
from ci.release_schema_mysql_forwarder_atomic_guard_scenario import verify_forwarder_atomic_guards  # noqa: E402
from ci.release_schema_mysql_forwarder_source_guard_scenario import verify_forwarder_source_drift_guard  # noqa: E402
from ci.release_schema_mysql_forwarder_eligibility_guard_scenario import verify_forwarder_eligibility_binary_guards  # noqa: E402
from ci.release_schema_mysql_forwarder_shape_guard_scenario import verify_forwarder_wrong_shape_fail_before_writes  # noqa: E402
from ci.release_schema_mysql_postcheck_diagnostics import apply_with_diagnostics  # noqa: E402
from ci.release_schema_mysql_noon_auth_wait_scenario import approve_noon_auth_wait, prepare_noon_auth_wait_fixture, verify_noon_auth_wait_migration  # noqa: E402

INTEGRITY_MIGRATION_KEY = "231_procurement_fulfillment_balance_quantity_invariant.sql"
REQUEST_IDEMPOTENCY_MIGRATION_KEY = "232_warehouse_command_request_idempotency.sql"
PACKING_INDEX_MIGRATION_KEY = "233_warehouse_packing_soft_delete_index.sql"
APPOINTMENT_MIGRATION_KEY = "234_official_warehouse_appointment_concurrency.sql"
SHIPPING_BATCH_MIGRATION_KEY = "235_warehouse_shipping_batch_request_idempotency.sql"
SHIPPING_PLAN_MIGRATION_KEY = "236_warehouse_shipping_batch_dispatch_plan_uniqueness.sql"

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
            expected_port=int(os.environ.get("NUONO_MIGRATION_EXPECTED_PORT", "3306")),
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
        prepare_current_release_fixture(database)
        prepare_noon_auth_wait_fixture(database)
        forwarder_fact_signature = prepare_forwarder_fixture(database, resources)
        integrity = next(item for item in migrations if item.key == INTEGRITY_MIGRATION_KEY)
        request_idempotency = next(
            item
            for item in migrations
            if item.key == REQUEST_IDEMPOTENCY_MIGRATION_KEY
        )
        packing_index = next(
            item for item in migrations if item.key == PACKING_INDEX_MIGRATION_KEY
        )
        appointment = next(item for item in migrations if item.key == APPOINTMENT_MIGRATION_KEY)
        shipping_batch = next(item for item in migrations if item.key == SHIPPING_BATCH_MIGRATION_KEY)
        shipping_plan = next(
            item for item in migrations
            if item.key == SHIPPING_PLAN_MIGRATION_KEY
        )
        forwarder = next(
            item for item in migrations if item.key == FORWARDER_MIGRATION_KEY
        )
        integrity_approval = (integrity.key,)
        approvals = list(integrity_approval)
        with self.assertRaisesRegex(MigrationError, "missing " + integrity.key):
            runner.apply()
        self.assertNotIn(integrity.key, database.load_states())
        with self.assertRaisesRegex(MigrationError, "missing " + appointment.key):
            runner.apply(approved_managed=approvals)
        self.assertNotIn(shipping_batch.key, database.load_states())
        approvals.append(appointment.key)
        with self.assertRaisesRegex(
                MigrationError, "missing " + shipping_plan.key
        ):
            runner.apply(approved_managed=approvals)
        self.assertNotIn(shipping_plan.key, database.load_states())
        approvals.append(shipping_plan.key)
        with self.assertRaisesRegex(MigrationError, "missing " + forwarder.key):
            runner.apply(approved_managed=approvals)
        self.assertNotIn(forwarder.key, database.load_states())
        approvals.append(forwarder.key)
        noon_auth_wait = approve_noon_auth_wait(self, runner, approvals, migrations)
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
                integrity.key, rerun=True, approved_managed=integrity_approval
            )
        database.client.execute(
            "ALTER TABLE procurement_fulfillment_balance DROP CHECK "
            "chk_fulfillment_balance_planned_nonnegative;"
        )
        repair_result = runner.repair_forward(
            integrity.key, rerun=True, approved_managed=integrity_approval
        )
        self.assertEqual("RERUN_APPLIED", repair_result)
        pre_forwarder_runner = MigrationRunner(
            database,
            migrations[:migrations.index(forwarder)],
            release_commit="a" * 40,
            installed_by="ci-mysql",
            lock_timeout_seconds=5,
        )
        pre_forwarder_approvals = [key for key in approvals
                                   if key not in (forwarder.key, noon_auth_wait.key)]
        expected_applied = [
            request_idempotency.key, packing_index.key, appointment.key,
            shipping_batch.key, shipping_plan.key,
        ]
        self.assertEqual(
            expected_applied,
            pre_forwarder_runner.apply(approved_managed=pre_forwarder_approvals),
        )
        verify_applied_schema(
            self,
            database,
            migrations[0],
            integrity,
            request_idempotency,
            packing_index,
        )
        verify_appointment_concurrency_migration(self, database, migrations)
        verify_shipping_batch_idempotency_migration(self, database, migrations)
        verify_shipping_batch_dispatch_plan_uniqueness_migration(
            self, database, migrations, pre_forwarder_runner
        )
        verify_forwarder_wrong_shape_fail_before_writes(self, database, forwarder)
        self.assertEqual(
            [forwarder.key, noon_auth_wait.key],
            apply_with_diagnostics(runner, approvals, database, forwarder),
        )
        self.assertTrue(all(state.state == "APPLIED"
                            for state in database.load_states().values()))
        verify_noon_auth_wait_migration(self, database, noon_auth_wait)
        verify_forwarder_migration(
            self, database, migrations, forwarder_fact_signature
        )
        verify_forwarder_source_drift_guard(self, database, forwarder)
        verify_forwarder_eligibility_binary_guards(self, database, forwarder)
        verify_forwarder_trigger_repair(self, database, forwarder)
        verify_forwarder_atomic_guards(self, database, forwarder)
        verify_lock_contention(self, database)
        verify_pre_catalog_bootstrap(
            self,
            database,
            resources,
            migrations,
            runner,
        )

        target = noon_auth_wait
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
            failing = build_probe_migration(
                root,
                failure_order,
                failure_key,
                "START TRANSACTION;CREATE TEMPORARY TABLE migration_failure_guard "
                "(invalid_count INT NOT NULL,CHECK(invalid_count=0)) ENGINE=InnoDB;"
                "INSERT INTO migration_failure_guard VALUES(1);COMMIT;\n",
                "SELECT 0;\n",
            )
            blocked = build_probe_migration(
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
            self.assertIsNone(database.client.lock_process)
            states = database.load_states()
            self.assertEqual("FAILED", states[failing.key].state)
            self.assertNotIn(blocked.key, states)
            self.assertEqual(
                "0\nFAILED/MYSQL_3819/FAILED/MYSQL_3819",
                database.client.execute(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    "WHERE table_schema=DATABASE() "
                    "AND table_name='migration_must_not_run';"
                    "SELECT CONCAT(h.state,'/',h.error_code,'/',a.state,'/',a.error_code) "
                    "FROM nuono_schema_migration h JOIN nuono_schema_migration_attempt a USING(migration_key,attempt_no) WHERE "
                    f"migration_key='{failing.key}';"
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
if __name__ == "__main__":
    unittest.main()
