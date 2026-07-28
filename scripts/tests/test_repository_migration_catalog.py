from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402


class RepositoryMigrationCatalogTest(unittest.TestCase):
    def test_catalog_owns_history_and_every_removed_runtime_schema(self):
        resource_root = SCRIPT_DIR.parent / "src/main/resources"

        migrations = load_catalog(resource_root)
        sql = "\n".join(migration.script_sql for migration in migrations)

        self.assertEqual(
            [
                "210_database_migration_history.sql",
                "211_noon_pull_runtime_schema_convergence.sql",
                "212_noon_fact_runtime_schema_convergence.sql",
                "213_noon_finance_runtime_schema_convergence.sql",
            ],
            [migration.key for migration in migrations],
        )
        self.assertEqual(
            ["BOOTSTRAP", "AUTO_ADDITIVE", "AUTO_ADDITIVE", "AUTO_ADDITIVE"],
            [migration.kind for migration in migrations],
        )
        for table in (
            "nuono_schema_migration",
            "noon_pull_smoke_run",
            "noon_pull_smoke_evidence",
            "noon_production_scheduler_enablement",
            "daily_sales_fact",
            "noon_order_line_fact",
            "noon_finance_transaction_fact",
        ):
            self.assertIn(f"CREATE TABLE IF NOT EXISTS `{table}`", sql)


if __name__ == "__main__":
    unittest.main()
