from pathlib import Path
import re
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from ci.release_schema_mysql_forwarder_scenario import FIXTURE_MIGRATIONS  # noqa: E402
from ci.release_schema_mysql_forwarder_source_contract import assert_source_contract  # noqa: E402


class WarehouseForwarderMigrationTest(unittest.TestCase):
    def test_source_contract_failure_identifies_expected_and_actual_digest(self):
        client = type("Client", (), {"execute": lambda self, statement: "fixture-digest"})()
        database = type("Database", (), {"client": client})()

        with self.assertRaisesRegex(
            AssertionError,
            "raw category digest mismatch: expected [0-9a-f]{64}, actual fixture-digest",
        ):
            assert_source_contract(database)

    def test_mysql_fixture_applies_reference_schema_before_route_cost_seed(self):
        self.assertEqual(
            (
                "030_logistics_quote_operations_v1.sql",
                "124_forwarder_quote_data_quality.sql",
                "128_procurement_logistics_route_cost_components.sql",
            ),
            FIXTURE_MIGRATIONS,
        )

    def test_materialization_is_managed_fail_closed_and_old_jar_compatible(self):
        resources = SCRIPT_DIR.parent / "src/main/resources"
        migration = next(
            item for item in load_catalog(resources)
            if item.key
            == "237_warehouse_forwarder_quote_and_transport_eligibility.sql"
        )
        script = migration.script_sql
        postcheck = migration.postcheck_sql

        self.assertEqual("MANAGED", migration.kind)
        for marker in (
            "YT-SAU-20260728",
            "2026-07-28",
            "1540.0000",
            "1900.0000",
            "2040.0000",
            "2290.0000",
            "@source_category_business_hash",
            "@target_category_business_hash",
            "@source_price_business_hash",
            "@target_price_business_hash",
            "@source_fee_business_hash",
            "@target_fee_business_hash",
            "@adjustment_hash_locked",
            "@fence_trigger_subset_exact",
            "@eligibility_object_count",
            "(@eligibility_object_count=0 OR @eligibility_existing_exact)",
            "FOR UPDATE",
            "@target_artifact_count=23",
            "product_forwarder_transport_eligibility",
            "eligibility_status_snapshot",
            "chk_pfte_status",
            "chk_pfte_scope_codes",
            "CAST(`eligibility_status` AS BINARY)",
            "UPPER(TRIM(`site_code`))",
            "utf8mb4_unicode_ci",
        ):
            self.assertIn(marker, script)
        self.assertLess(
            script.index("nuono_237_eligibility_state_guard"),
            script.index("CREATE TRIGGER IF NOT EXISTS"),
        )
        for digest in (
            "9cf247aea2f146265c979b3467bcfb6e41a2a864f7da226ef4789171b82bd444",
            "025a8cfa78920deaff035819431e45742a6ee2830f1c1e010ef36383f5c82db2",
            "83caf487c8953f0eff04ef719e5482a65158a4e3773f1176802838baf9e03245",
            "a8ea877d8cc8fdbd249c2ea716f9cea0316b031c9d104419bb44f34e056290cf",
            "2be8542906f265bc3cdcf60c763f0fe949b51e15c5f843064a77481065e40029",
            "088dff7da968d51e58fea26398acf661e329218397fba05faa657a4768930e30",
            "902b6173f5ee366a03a79f282777a67579ab8262598bdab89e588533cfd19ff1",
            "74ff49fbd0863e298bbb9244a8db8c2429e12ce84d9dbf7dd7ac2a8df9e832f8",
        ):
            self.assertIn(digest, script)
            self.assertIn(digest, postcheck)

        legacy_tables = (
            "forwarder_quote_numeric_adjustment",
            "forwarder_quote_numeric_adjustment_log",
        )
        for table in legacy_tables:
            self.assertNotRegex(
                script,
                re.compile(
                    rf"\b(?:DROP|ALTER|TRUNCATE)\s+TABLE\s+`?{table}\b"
                    rf"|\b(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?{table}\b",
                    re.IGNORECASE,
                ),
            )
        self.assertNotIn("product_forwarder_channel_quote", script)
        self.assertNotIn("product_logistics_current_cost", script)
        self.assertNotRegex(
            script,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM|INSERT\s+INTO)\s+"
                r"`?procurement_shipping_order_line\b",
                re.IGNORECASE,
            ),
        )
        for sql in (script, postcheck):
            self.assertIn("COALESCE(adjustment.adjusted_value,price.unit_price)", sql)
            self.assertIn("adjustment.id IS NULL", sql)
            self.assertIn("uk_pfte_active_scope", sql)


if __name__ == "__main__":
    unittest.main()
