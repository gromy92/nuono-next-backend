from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402


class OfficialWarehouseCollationMigrationContractTest(unittest.TestCase):
    def test_scope_collation_alignment_is_managed_and_exact(self):
        resource_root = SCRIPT_DIR.parent / "src/main/resources"
        migration = next(
            item for item in load_catalog(resource_root)
            if item.key == "239_official_warehouse_scope_collation_alignment.sql"
        )

        self.assertEqual("MANAGED", migration.kind)
        for sql in (migration.script_sql, migration.postcheck_sql):
            for marker in (
                "official_warehouse_asn_shipping_batch_link",
                "utf8mb4_0900_ai_ci",
                "store_code",
                "site_code",
                "idx_official_warehouse_asn_shipping_product",
            ):
                self.assertIn(marker, sql)
        self.assertIn("utf8mb4_unicode_ci", migration.script_sql)
        self.assertIn("@scope_parent_mismatch_count", migration.script_sql)
        self.assertIn("UPPER(parent_asn.store_code)", migration.script_sql)
        self.assertIn("COLLATE utf8mb4_0900_ai_ci", migration.script_sql)
        self.assertIn("DEFAULT CHARACTER SET utf8mb4", migration.script_sql)
        self.assertIn(
            "MODIFY COLUMN `store_code` VARCHAR(100) CHARACTER SET utf8mb4 "
            "COLLATE utf8mb4_0900_ai_ci NOT NULL",
            migration.script_sql,
        )
        self.assertIn(
            "MODIFY COLUMN `site_code` VARCHAR(20) CHARACTER SET utf8mb4 "
            "COLLATE utf8mb4_0900_ai_ci NOT NULL",
            migration.script_sql,
        )
        self.assertNotRegex(
            migration.script_sql,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM)\s+"
                r"`?official_warehouse_asn_shipping_batch_link\b",
                re.IGNORECASE,
            ),
        )
        self.assertNotIn("CONVERT TO CHARACTER SET", migration.script_sql.upper())

        bootstrap = (
            resource_root
            / "db/init/144_official_warehouse_asn_shipping_batch_link.sql"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", bootstrap
        )
        self.assertNotIn(
            "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", bootstrap
        )


if __name__ == "__main__":
    unittest.main()
