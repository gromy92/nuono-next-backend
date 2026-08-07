from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog, sha256_bytes  # noqa: E402
from ci.release_schema_mysql_pre_catalog_scenario import (  # noqa: E402
    PUBLISHED_PRE_CATALOG_SHA256,
)


class RepositoryMigrationCatalogTest(unittest.TestCase):
    RESOURCE_ROOT = SCRIPT_DIR.parent / "src/main/resources"

    def migration(self, key):
        return next(item for item in load_catalog(self.RESOURCE_ROOT) if item.key == key)

    def test_published_pre_catalog_migrations_are_immutable_and_not_cataloged(self):
        catalog_keys = {
            migration.key for migration in load_catalog(self.RESOURCE_ROOT)
        }

        for key, expected_sha256 in PUBLISHED_PRE_CATALOG_SHA256.items():
            with self.subTest(key=key):
                published = self.RESOURCE_ROOT / "db/init" / key
                self.assertEqual(
                    expected_sha256,
                    sha256_bytes(published.read_bytes()),
                )
                self.assertNotIn(key, catalog_keys)

    def test_catalog_owns_history_and_every_removed_runtime_schema(self):
        migrations = load_catalog(self.RESOURCE_ROOT)
        sql = "\n".join(migration.script_sql for migration in migrations)

        self.assertEqual(
            [
                "227_database_migration_history.sql",
                "228_noon_pull_runtime_schema_convergence.sql",
                "229_noon_fact_runtime_schema_convergence.sql",
                "230_noon_finance_runtime_schema_convergence.sql",
                "231_procurement_fulfillment_balance_quantity_invariant.sql",
                "232_warehouse_command_request_idempotency.sql",
                "233_warehouse_packing_soft_delete_index.sql",
                "234_official_warehouse_appointment_concurrency.sql",
                "235_warehouse_shipping_batch_request_idempotency.sql",
                "236_warehouse_shipping_batch_dispatch_plan_uniqueness.sql",
                "237_warehouse_forwarder_quote_and_transport_eligibility.sql",
                "238_noon_auth_business_wait_queue.sql",
                "239_official_warehouse_scope_collation_alignment.sql",
                "240_operations_competitor_snapshot_active_uniqueness.sql", "241_operations_competitor_correction_writer_fence.sql",
                "242_file_management_parse_retirement.sql", "243_dp_pull_runtime.sql",
                "244_dp_pull_report_bounded_apply.sql",
                "245_dp_pull_snapshot_bounded_apply.sql",
                "246_dp_pull_advertising_generation.sql",
                "247_dp_pull_schedule_core.sql",
                "248_dp_pull_dp08_member_retention.sql",
                "249_official_warehouse_asn_line_source_allocation.sql",
                "250_dp_pull_advertising_campaign_pagination.sql",
                "251_noon_auth_owner_scope_successor.sql",
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
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
                "MANAGED",
                "AUTO_ADDITIVE",
                "MANAGED",
                "MANAGED",
                "MANAGED",
                "MANAGED",
                "MANAGED",
                "MANAGED",
                "MANAGED",
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
                "AUTO_ADDITIVE",
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
        migration = self.migration(
            "231_procurement_fulfillment_balance_quantity_invariant.sql"
        )
        script = migration.script_sql
        postcheck = migration.postcheck_sql
        compact_script = self.compact(script)

        self.assertEqual(
            "231_procurement_fulfillment_balance_quantity_invariant.sql",
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

    def test_warehouse_request_idempotency_is_additive_and_fail_closed(self):
        migration = self.migration("232_warehouse_command_request_idempotency.sql")

        self.assertEqual("AUTO_ADDITIVE", migration.kind)
        self.assertIn("duplicate_group_count", migration.script_sql)
        self.assertIn("uk_dispatch_plan_owner_client_request", migration.script_sql)
        self.assertIn(
            "uk_fulfillment_confirmation_owner_client_request",
            migration.script_sql,
        )
        self.assertIn(
            "1:owner_user_id,2:client_request_id",
            migration.postcheck_sql,
        )
        for sql in (migration.script_sql, migration.postcheck_sql):
            self.assertIn(
                "`request_fingerprint` COLLATE utf8mb4_bin "
                "REGEXP '^[0-9a-f]{64}$'",
                sql,
            )
            self.assertNotIn("BINARY `request_fingerprint` REGEXP", sql)
        self.assertNotRegex(
            migration.script_sql,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM)\s+"
                r"`?(?:procurement_dispatch_plan|"
                r"procurement_fulfillment_confirmation)\b",
                re.IGNORECASE,
            ),
        )

    def test_packing_soft_delete_index_is_exact_and_additive(self):
        migration = self.migration("233_warehouse_packing_soft_delete_index.sql")

        self.assertEqual("AUTO_ADDITIVE", migration.kind)
        self.assertIn("idx_packing_box_item_list", migration.script_sql)
        self.assertIn(
            "ADD KEY `idx_packing_box_item_list` "
            "(`packing_list_id`, `is_deleted`)",
            migration.script_sql,
        )
        for sql in (migration.script_sql, migration.postcheck_sql):
            self.assertIn("1:packing_list_id,2:is_deleted", sql)
            self.assertIn("is_visible = 'YES'", sql)
            self.assertIn("expression IS NULL", sql)
        self.assertNotRegex(
            migration.script_sql,
            re.compile(
                r"\bDELETE\s+FROM\s+`?warehouse_packing_box_item\b",
                re.IGNORECASE,
            ),
        )

    def test_appointment_concurrency_is_managed_and_fail_closed(self):
        migration = self.migration("234_official_warehouse_appointment_concurrency.sql")

        self.assertEqual("MANAGED", migration.kind)
        for marker in (
            "execution_version",
            "uk_official_warehouse_appointment_active_asn",
            "uk_official_warehouse_appointment_active_remote",
            "CHAR_LENGTH(UPPER(TRIM(COALESCE(`project_code`, ''))))",
            "@appointment_parent_mismatch_count",
            "@appointment_running_count",
            "@appointment_parent_scope_column_count",
            "NOT (parent_asn.owner_user_id <=> appointment.owner_user_id)",
        ):
            self.assertIn(marker, migration.script_sql + migration.postcheck_sql)
        for sql in (migration.script_sql, migration.postcheck_sql):
            self.assertIn(
                "REPLACE(REGEXP_REPLACE(LOWER(REPLACE(REPLACE", sql
            )
            self.assertNotIn(
                "LOWER(REGEXP_REPLACE(REPLACE", sql
            )
            self.assertIn("'(_utf8mb4)?''canceled'''", sql)
            self.assertNotIn("'(_utf8mb4)?''CANCELED'''", sql)
        for marker in ("column_name = 'id'", "column_name = 'owner_user_id'",
                       "column_name = 'store_code'", "column_name = 'attempt_count'"):
            self.assertIn(marker, migration.postcheck_sql)
        self.assertIn("HAVING COUNT(*) > 1", migration.postcheck_sql)
        self.assertIn("nuono_234_appointment_data_guard", migration.script_sql)
        self.assertNotIn("nuono_217_", migration.script_sql)

    def test_shipping_batch_request_idempotency_is_additive_and_exact(self):
        migration = self.migration("235_warehouse_shipping_batch_request_idempotency.sql")

        self.assertEqual("AUTO_ADDITIVE", migration.kind)
        for marker in (
            "VARCHAR(100) CHARACTER SET utf8mb4",
            "COLLATE utf8mb4_bin NULL DEFAULT NULL",
            "CHAR(64) CHARACTER SET ascii",
            "COLLATE ascii_bin NULL DEFAULT NULL",
            "uk_shipping_batch_owner_client_request",
            "1:owner_user_id,2:client_request_id",
            "HAVING COUNT(*) > 1",
        ):
            self.assertIn(marker, migration.script_sql + migration.postcheck_sql)
        for sql in (migration.script_sql, migration.postcheck_sql):
            self.assertIn(
                "BINARY `client_request_id` <> BINARY TRIM(`client_request_id`)", sql
            )
            self.assertIn("`client_request_id` REGEXP '[[:cntrl:]]'", sql)
        self.assertNotRegex(
            migration.script_sql,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM)\s+`?warehouse_shipping_batch\b",
                re.IGNORECASE,
            ),
        )
        self.assertNotIn("nuono_218_", migration.script_sql)

    @staticmethod
    def compact(sql: str) -> str:
        return re.sub(r"[\s`(){};]", "", sql.lower())

if __name__ == "__main__":
    unittest.main()
