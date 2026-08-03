from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402


class PreCatalogBaselineGateTest(unittest.TestCase):
    def test_catalog_temporary_guards_use_rds_compatible_innodb(self):
        resource_root = SCRIPT_DIR.parent / "src/main/resources"

        for migration in load_catalog(resource_root):
            with self.subTest(migration=migration.key):
                temporary_table_count = len(re.findall(
                    r"CREATE\s+TEMPORARY\s+TABLE\b",
                    migration.script_sql,
                    re.IGNORECASE,
                ))
                temporary_table_engines = re.findall(
                    r"CREATE\s+TEMPORARY\s+TABLE\b.*?"
                    r"ENGINE\s*=\s*([A-Za-z0-9_]+)\s*;",
                    migration.script_sql,
                    re.IGNORECASE | re.DOTALL,
                )

                self.assertEqual(
                    temporary_table_count,
                    len(temporary_table_engines),
                )
                self.assertEqual(
                    ["INNODB"] * temporary_table_count,
                    [engine.upper() for engine in temporary_table_engines],
                )
                self.assertNotRegex(
                    migration.script_sql,
                    re.compile(
                        r"ENGINE\s*=\s*(?:MEMORY|MYISAM|ARCHIVE)\b",
                        re.IGNORECASE,
                    ),
                )

    def test_bootstrap_fails_closed_before_creating_history(self):
        resource_root = SCRIPT_DIR.parent / "src/main/resources"
        bootstrap = load_catalog(resource_root)[0]
        script = bootstrap.script_sql
        postcheck = bootstrap.postcheck_sql

        self.assertEqual(227, bootstrap.order)
        self.assertEqual("227_database_migration_history.sql", bootstrap.key)
        self.assertEqual("BOOTSTRAP", bootstrap.kind)

        guard_position = script.index(
            "INSERT INTO `nuono_227_pre_catalog_baseline_guard`"
        )
        history_position = script.index(
            "CREATE TABLE IF NOT EXISTS `nuono_schema_migration`"
        )
        self.assertLess(guard_position, history_position)

        for marker in (
            "@pre_catalog_table_count = 2",
            "@pre_catalog_223_columns_exact = 1",
            "@pre_catalog_223_index_exact = 1",
            "@pre_catalog_224_partner_index_exact = 1",
            "@pre_catalog_224_lookup_index_exact = 1",
            "@pre_catalog_224_legacy_index_count = 0",
            "chk_227_pre_catalog_baseline",
            "idx_product_site_offer_replenishment_coverage",
            "uk_product_master_store_partner_sku",
            "idx_product_master_store_sku_parent_lookup",
            "uk_product_master_store_sku_parent",
        ):
            self.assertIn(marker, script)

        index_contracts = {
            "pre_catalog_223_index_exact": (
                "idx_product_site_offer_replenishment_coverage",
                "1:logical_store_id,2:site_id,3:maintenance_enabled,4:is_active",
            ),
            "pre_catalog_224_partner_index_exact": (
                "uk_product_master_store_partner_sku",
                "1:logical_store_id,2:partner_sku",
            ),
            "pre_catalog_224_lookup_index_exact": (
                "idx_product_master_store_sku_parent_lookup",
                "1:logical_store_id,2:sku_parent,3:is_deleted",
            ),
        }
        for variable, (index_name, shape) in index_contracts.items():
            with self.subTest(variable=variable):
                match = re.search(
                    rf"SET @{variable} := \((.*?)\n\);",
                    script,
                    re.DOTALL,
                )
                self.assertIsNotNone(match)
                self.assertIn(index_name, match.group(1))
                self.assertIn(shape, match.group(1))

        self.assertNotRegex(
            script,
            re.compile(
                r"\b(?:ALTER\s+TABLE|"
                r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?|"
                r"UPDATE|DELETE\s+FROM|INSERT\s+INTO|REPLACE\s+INTO)\s+`?"
                r"(?:product_site_offer|product_master)\b",
                re.IGNORECASE,
            ),
        )
        self.assertNotIn("product_site_offer", postcheck)
        self.assertNotIn("product_master", postcheck)


if __name__ == "__main__":
    unittest.main()
