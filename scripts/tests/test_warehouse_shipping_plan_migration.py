from pathlib import Path
import re
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402


class WarehouseShippingPlanMigrationTest(unittest.TestCase):
    def test_uniqueness_migration_is_managed_and_fail_closed(self):
        resource_root = SCRIPT_DIR.parent / "src/main/resources"
        migration = next(
            item for item in load_catalog(resource_root)
            if item.key
            == "236_warehouse_shipping_batch_dispatch_plan_uniqueness.sql"
        )
        script = migration.script_sql
        postcheck = migration.postcheck_sql
        compact_script = re.sub(r"[\s`(){};]", "", script.lower())

        self.assertEqual("MANAGED", migration.kind)
        self.assertIn(
            "addcolumnactive_dispatch_plan_idbigintgeneratedalwaysas"
            "casewhenis_deleted=b''0''thendispatch_plan_idelsenullendstored",
            compact_script,
        )
        self.assertIn(
            "adduniquekeyuk_shipping_batch_active_dispatch_plan"
            "active_dispatch_plan_id",
            compact_script,
        )
        self.assertNotIn(
            "uk_shipping_batch_active_dispatch_plan"
            "owner_user_id,active_dispatch_plan_id",
            compact_script,
        )
        for guard in (
            "shipping_plan_duplicate_active_count",
            "shipping_plan_orphan_count",
            "shipping_plan_deleted_parent_count",
            "shipping_plan_owner_mismatch_count",
        ):
            self.assertIn(guard, script)
        self.assertIn("invalid_data_count", script)
        self.assertIn("checkinvalid_data_count=0", compact_script)
        self.assertIn("nuono_236_shipping_plan_link_base_guard", script)
        self.assertIn("@shipping_plan_parent_primary_index_exact", script)
        self.assertNotIn("nuono_220_", script)
        self.assertNotRegex(
            script,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM)\s+`?warehouse_shipping_batch\b",
                re.IGNORECASE,
            ),
        )
        for sql in (script, postcheck):
            self.assertIn("table_name = 'procurement_dispatch_plan'", sql)
            self.assertIn("index_name = 'PRIMARY'", sql)
            self.assertIn("MIN(column_name) = 'id'", sql)
            self.assertIn("idx_shipping_batch_dispatch_plan", sql)
            self.assertIn("active_dispatch_plan_id", sql)
            self.assertIn("uk_shipping_batch_active_dispatch_plan", sql)
            self.assertIn("dispatch_plan_id IS NOT NULL", sql)
            self.assertIn("batch.owner_user_id <=> plan.owner_user_id", sql)
            self.assertIn("is_visible = 'YES'", sql)
            self.assertIn("expression IS NULL", sql)
            self.assertIn("CONCAT(CHAR(92), CHAR(39))", sql)
            self.assertIn("CONCAT(CHAR(92), '0')", sql)
        self.assertNotIn("active_dispatch_plan_id <=>", postcheck)


if __name__ == "__main__":
    unittest.main()
