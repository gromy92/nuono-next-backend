from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class RdsMigrationCiContractTest(unittest.TestCase):
    def test_ci_runs_catalog_on_exact_production_disabled_engine_policy(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        mysql_suite = (ROOT / "scripts/ci/test_release_schema_migrations_mysql.py").read_text(
            encoding="utf-8"
        )

        self.assertIn("mysql:8.0.36", workflow)
        self.assertIn(
            "--disabled-storage-engines=MyISAM,MEMORY,ARCHIVE",
            workflow,
        )
        self.assertIn("myisam,memory,archive", workflow)
        self.assertIn("NUONO_MIGRATION_EXPECTED_PORT", workflow)
        self.assertIn("NUONO_MIGRATION_EXPECTED_PORT", mysql_suite)

    def test_disabled_engine_fixture_keeps_wrong_shape_on_innodb(self):
        scenario = (
            ROOT / "scripts/ci/release_schema_mysql_forwarder_shape_guard_scenario.py"
        ).read_text(encoding="utf-8")

        self.assertNotIn("ENGINE=MyISAM", scenario)
        self.assertIn(
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci",
            scenario,
        )

    def test_runner_failure_probe_exercises_open_transaction_recovery(self):
        mysql_suite = (
            ROOT / "scripts/ci/test_release_schema_migrations_mysql.py"
        ).read_text(encoding="utf-8")

        self.assertIn("START TRANSACTION;CREATE TEMPORARY TABLE", mysql_suite)
        self.assertIn("MYSQL_3819", mysql_suite)
        self.assertIn("a.error_code", mysql_suite)
        self.assertIn("assertIsNone(database.client.lock_process)", mysql_suite)


if __name__ == "__main__":
    unittest.main()
