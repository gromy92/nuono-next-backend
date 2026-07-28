from __future__ import annotations

import re
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
                "211_database_migration_history.sql",
                "212_noon_pull_runtime_schema_convergence.sql",
                "213_noon_fact_runtime_schema_convergence.sql",
                "214_noon_finance_runtime_schema_convergence.sql",
                "215_procurement_fulfillment_balance_quantity_invariant.sql",
            ],
            [migration.key for migration in migrations],
        )
        self.assertEqual(
            [
                "BOOTSTRAP",
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
                "MANAGED",
            ],
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

    def test_fulfillment_balance_invariant_is_fail_closed(self):
        resource_root = SCRIPT_DIR.parent / "src/main/resources"
        migration = load_catalog(resource_root)[-1]
        script = migration.script_sql
        postcheck = migration.postcheck_sql
        compact_script = self.compact(script)

        self.assertEqual(
            "215_procurement_fulfillment_balance_quantity_invariant.sql",
            migration.key,
        )
        self.assertEqual("MANAGED", migration.kind)
        constraint_names = {
            "chk_fulfillment_balance_planned_nonnegative",
            "chk_fulfillment_balance_confirmed_nonnegative",
            "chk_fulfillment_balance_abnormal_nonnegative",
            "chk_fulfillment_balance_reserved_nonnegative",
            "chk_fulfillment_balance_logistics_handoff_nonnegative",
            "chk_fulfillment_balance_available_nonnegative",
            "chk_fulfillment_balance_quantity_conservation",
        }
        self.assertEqual(
            constraint_names,
            set(re.findall(r"ADD CONSTRAINT `([^`]+)`", script)),
        )
        for constraint_name in constraint_names:
            self.assertIn(constraint_name, postcheck)
        self.assertIn("invalid_row_count", script)
        self.assertIn("checkinvalid_row_count=0", compact_script)
        for column in (
            "planned_quantity",
            "confirmed_quantity",
            "abnormal_quantity",
            "reserved_quantity",
            "logistics_handoff_quantity",
            "available_quantity",
        ):
            self.assertIn(f"check{column}>=0enforced", compact_script)
        self.assertIn(
            "confirmed_quantity=abnormal_quantity+reserved_quantity"
            "+logistics_handoff_quantity+available_quantity",
            compact_script,
        )
        for sql in (script, postcheck):
            self.assertIn("'[[:space:]`]+'", sql)
            self.assertNotIn("'[[:space:]`()]+'", sql)
            self.assertIn("BINARY constraints.constraint_name", sql)
        self.assertNotRegex(
            script,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM)\s+"
                r"`?procurement_fulfillment_balance",
                re.IGNORECASE,
            ),
        )
        self.assertIn("information_schema.check_constraints", postcheck)
        self.assertIn("information_schema.table_constraints", postcheck)
        self.assertIn(
            "BINARY constraints.enforced = BINARY 'YES'",
            postcheck,
        )
        self.assertNotRegex(
            postcheck,
            re.compile(
                r"\bFROM\s+`?procurement_fulfillment_balance\b",
                re.IGNORECASE,
            ),
        )

    @staticmethod
    def compact(sql: str) -> str:
        return re.sub(r"[\s`(){};]", "", sql.lower())


if __name__ == "__main__":
    unittest.main()
