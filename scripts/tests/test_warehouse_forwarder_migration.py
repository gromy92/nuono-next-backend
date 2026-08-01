from pathlib import Path
import hashlib
import re
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from ci.release_schema_mysql_forwarder_scenario import FIXTURE_MIGRATIONS  # noqa: E402
from ci.release_schema_mysql_forwarder_source_contract import (  # noqa: E402
    GROUP_CONCAT_SESSION_SQL,
    SOURCE_CATEGORY_HASH,
    SOURCE_FEE_HASH,
    SOURCE_PRICE_HASH,
    SOURCE_RAW_CATEGORY_HASH,
    SOURCE_RAW_PRICE_HASH,
    assert_source_contract,
    execute_group_concat,
)
from ci.release_schema_mysql_postcheck_diagnostics import (  # noqa: E402
    outer_if_predicates,
)


def stable_scope_slot(owner, store, partner_sku, site, forwarder, mode):
    values = (
        str(owner),
        str(store),
        partner_sku.strip().upper(),
        site.strip().upper(),
        forwarder.strip().upper(),
        mode.strip().upper(),
    )
    return "".join(f"{len(value.encode('utf-8'))}#{value}" for value in values)


def decode_scope_slot(slot):
    encoded = slot.encode("utf-8")
    values = []
    offset = 0
    for _ in range(6):
        separator = encoded.index(b"#", offset)
        length = int(encoded[offset:separator])
        start = separator + 1
        end = start + length
        values.append(encoded[start:end].decode("utf-8"))
        offset = end
    if offset != len(encoded):
        raise AssertionError("scope slot has trailing bytes")
    return tuple(values)


class WarehouseForwarderMigrationTest(unittest.TestCase):
    def test_stable_scope_slot_uses_utf8_byte_lengths_without_delimiters(self):
        basic = stable_scope_slot(307, 108065, "SKU|#1", "SA", "ET", "AIR")
        self.assertEqual("3#3076#1080656#SKU|#12#SA2#ET3#AIR", basic)
        self.assertEqual(
            ("307", "108065", "SKU|#1", "SA", "ET", "AIR"),
            decode_scope_slot(basic),
        )

        injected = stable_scope_slot(
            307, 108065, "货号#|一", "沙特", "承运|#商", "SEA"
        )
        self.assertEqual(
            ("307", "108065", "货号#|一", "沙特", "承运|#商", "SEA"),
            decode_scope_slot(injected),
        )
        self.assertIn("11#货号#|一", injected)
        self.assertNotEqual(
            stable_scope_slot(1, 23, "SKU", "SA", "ET", "AIR"),
            stable_scope_slot(12, 3, "SKU", "SA", "ET", "AIR"),
        )

        maximum = stable_scope_slot(
            9223372036854775807,
            9223372036854775807,
            "🧰" * 100,
            "站" * 20,
            "运" * 80,
            "SEA",
        )
        self.assertEqual("🧰" * 100, decode_scope_slot(maximum)[2])
        self.assertIn("400#" + "🧰" * 100, maximum)
        self.assertLessEqual(len(maximum), 512)

    def test_postcheck_diagnostics_split_only_outer_contract_predicates(self):
        resources = SCRIPT_DIR.parent / "src/main/resources"
        migration = next(
            item for item in load_catalog(resources) if item.key ==
            "237_warehouse_forwarder_quote_and_transport_eligibility.sql"
        )

        predicates = outer_if_predicates(migration.postcheck_sql)

        self.assertGreater(len(predicates), 40)
        self.assertIn("version_no='YT-SAU-20260728'", predicates[0])
        self.assertIn("product_management_id_sequence", predicates[-1])

    def test_ci_matches_production_trigger_policy_without_elevating_migration_user(self):
        workflow = (SCRIPT_DIR.parent / ".github/workflows/ci.yml").read_text(
            encoding="utf-8"
        )
        policy = "SET GLOBAL log_bin_trust_function_creators=ON;"
        migration_test = (
            "python3 -m unittest scripts/ci/test_release_schema_migrations_mysql.py"
        )

        self.assertIn(policy, workflow)
        self.assertIn("SELECT @@GLOBAL.log_bin_trust_function_creators;", workflow)
        self.assertLess(workflow.index(policy), workflow.index(migration_test))
        self.assertIn("'user=migration_ci'", workflow)

    def test_group_concat_limit_is_set_in_the_same_mysql_invocation(self):
        statements = []
        client = type(
            "Client",
            (),
            {"execute": lambda self, statement: statements.append(statement) or "digest"},
        )()
        database = type("Database", (), {"client": client})()

        self.assertEqual("digest", execute_group_concat(database, "SELECT 'digest';"))
        self.assertEqual(
            f"{GROUP_CONCAT_SESSION_SQL}\nSELECT 'digest';",
            statements[0],
        )

    def test_source_contract_sets_group_concat_limit_for_every_digest(self):
        statements = []
        digests = iter(
            (
                SOURCE_RAW_CATEGORY_HASH,
                SOURCE_RAW_PRICE_HASH,
                SOURCE_CATEGORY_HASH,
                SOURCE_PRICE_HASH,
                SOURCE_FEE_HASH,
            )
        )
        client = type(
            "Client",
            (),
            {
                "execute": lambda self, statement: statements.append(statement)
                or next(digests)
            },
        )()
        database = type("Database", (), {"client": client})()

        assert_source_contract(database)

        self.assertEqual(5, len(statements))
        for statement in statements:
            self.assertTrue(statement.startswith(f"{GROUP_CONCAT_SESSION_SQL}\nSELECT"))

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
        migration_path = resources / migration.script_path
        postcheck_path = resources / migration.postcheck_path

        self.assertEqual("MANAGED", migration.kind)
        self.assertEqual(
            hashlib.sha256(migration_path.read_bytes()).hexdigest(),
            migration.checksum,
        )
        self.assertEqual(
            hashlib.sha256(postcheck_path.read_bytes()).hexdigest(),
            migration.postcheck_checksum,
        )
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
            "@fence_trigger_subset_exact", "action_order=1",
            "@eligibility_object_count",
            "@eligibility_known_old_exact",
            "@eligibility_target_exact",
            "@eligibility_rebuild_known_old",
            "@eligibility_locked_row_count",
            "@eligibility_locked_drop_allowed",
            "LOCK TABLES `product_forwarder_transport_eligibility` WRITE", "LOCK TABLES `product_forwarder_eligibility_scope_anchor` WRITE,`product_forwarder_transport_eligibility` WRITE",
            "UNLOCK TABLES",
            "FOR UPDATE",
            "@target_artifact_count=23 AND (SELECT COUNT(*) FROM forwarder_quote_service_line WHERE quote_version_id=@new_version_id)=1",
            "product_forwarder_transport_eligibility",
            "product_forwarder_eligibility_scope_anchor",
            "eligibility_status_snapshot",
            "chk_pfte_status",
            "chk_pfte_product_scope",
            "chk_pfte_scope_codes",
            "CAST(`eligibility_status` AS BINARY)",
            "OCTET_LENGTH(CAST(`owner_user_id` AS CHAR))",
            "VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin",
            "idx_pfte_scope_history",
            "UPPER(TRIM(`site_code`))",
            "utf8mb4_unicode_ci", "@eligibility_object_count=0 AND @anchor_object_count=0", "@eligibility_final_locked_target_exact", "@target_category_business_hash_before", "@target_final_locked", "SELECT id FROM procurement_shipping_order_line WHERE id=-1 FOR UPDATE",
        ):
            self.assertIn(marker, script)
        self.assertLess(
            script.index("nuono_237_eligibility_state_guard"),
            script.index("CREATE TRIGGER IF NOT EXISTS"),
        )
        self.assertLess(script.index("LOCK TABLES"), script.index("@eligibility_locked_row_count")); self.assertLess(script.index("@target_category_business_hash_before"), script.index("nuono_237_base_state_guard")); self.assertLess(script.index("@target_final_locked"), script.index("INSERT INTO forwarder_quote_version"))
        self.assertLess(
            script.index("@eligibility_locked_row_count"),
            script.index("@eligibility_drop_sql"),
        )
        self.assertLess(script.index("@eligibility_drop_sql"), script.index("UNLOCK TABLES")); self.assertLess(script.index("@snapshot_sql"), script.index("START TRANSACTION")); self.assertLess(script.index("@snapshot_locked_exact"), script.index("INSERT INTO forwarder_quote_version"))
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

        legacy_tables = ("forwarder_quote_numeric_adjustment", "forwarder_quote_numeric_adjustment_log")
        for table in legacy_tables:
            self.assertNotRegex(
                script,
                re.compile(
                    rf"\b(?:DROP|ALTER|TRUNCATE)\s+TABLE\s+`?{table}\b"
                    rf"|\b(?:INSERT\s+INTO|UPDATE|DELETE\s+FROM)\s+`?{table}\b",
                    re.IGNORECASE,
                ),
            )
        self.assertNotIn("product_forwarder_channel_quote", script); self.assertNotIn("product_logistics_current_cost", script)
        self.assertNotRegex(
            script,
            re.compile(
                r"\b(?:UPDATE|DELETE\s+FROM|INSERT\s+INTO)\s+"
                r"`?procurement_shipping_order_line\b",
                re.IGNORECASE,
            ),
        )
        for sql in (script, postcheck):
            self.assertTrue(all(marker in sql for marker in ("event_object_table='product_forwarder_transport_eligibility'", "event_object_table='product_forwarder_eligibility_scope_anchor'")))
            self.assertIn("COALESCE(adjustment.adjusted_value,price.unit_price)", sql); self.assertIn("adjustment.id IS NULL", sql)
            self.assertIn("uk_pfte_active_scope", sql); self.assertIn("CONCAT(CHAR(92),CHAR(39)),CHAR(39)", sql)
            self.assertIn("CONCAT(CHAR(92),'0'),'0'", sql)
            self.assertIn("[[:space:]]*=[[:space:]]*", sql)
            self.assertIn("'charcharsetbinary','binary'", sql)
            self.assertIn("'octet_length','length'", sql)
            self.assertIn("'charactersetutf8mb4',''", sql); self.assertIn("action_order=1", sql)

    def test_mysql8_index_visibility_uses_information_schema_is_visible(self):
        paths = ("src/main/resources/db/init/237_warehouse_forwarder_quote_and_transport_eligibility.sql", "src/main/resources/db/postcheck/237_warehouse_forwarder_quote_and_transport_eligibility.sql", "scripts/ci/release_schema_mysql_forwarder_shape_guard_scenario.py", "scripts/ci/release_schema_mysql_forwarder_atomic_guard_scenario.py")
        for relative in paths:
            content = (SCRIPT_DIR.parent / relative).read_text(encoding="utf-8")
            with self.subTest(path=relative): self.assertNotRegex(content, r"(?<!is_)\bvisible\b"); self.assertIn("is_visible", content)


if __name__ == "__main__":
    unittest.main()
