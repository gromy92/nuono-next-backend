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


@unittest.skipUnless(
    os.environ.get("NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"),
    "requires an isolated MySQL schema",
)
class ReleaseSchemaMigrationsMySqlTest(unittest.TestCase):
    def test_apply_idempotency_lock_drift_and_repair_forward(self):
        defaults_file = Path(
            os.environ["NUONO_MIGRATION_MYSQL_DEFAULTS_FILE"]
        )
        expected_schema = os.environ.get(
            "NUONO_MIGRATION_EXPECTED_SCHEMA",
            "nuono_schema_migration_ci",
        )
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
        self.assertEqual([], runner.apply())
        self.assertTrue(
            all(state.state == "APPLIED" for state in database.load_states().values())
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

        target = migrations[-1]
        database.client.execute(
            "UPDATE nuono_schema_migration SET state='FAILED' "
            f"WHERE migration_key='{target.key}';"
            "UPDATE nuono_schema_migration_attempt SET state='FAILED' "
            f"WHERE migration_key='{target.key}' AND attempt_no=1;"
        )
        self.assertEqual(
            "POSTCHECK_RECONCILED",
            runner.repair_forward(target.key),
        )

        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            failing = self.migration(
                root,
                214,
                "214_failure_probe.sql",
                "CREATE TABLE migration_failure_before (id INT);\n"
                "THIS IS NOT VALID SQL;\n"
                "CREATE TABLE migration_failure_after (id INT);\n",
                "SELECT 0;\n",
            )
            blocked = self.migration(
                root,
                215,
                "215_must_not_run.sql",
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
            with self.assertRaisesRegex(MigrationError, "214_failure_probe"):
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
