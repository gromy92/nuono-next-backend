from __future__ import annotations

import sys
import unittest
from pathlib import Path, PurePosixPath

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402


class MigrationLivecheckContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.migrations = load_catalog(
            SCRIPT_DIR.parent / "src/main/resources"
        )

    def test_default_remains_as_strict_as_the_original_postcheck(self):
        for migration in self.migrations:
            if migration.order == 237:
                continue
            with self.subTest(migration=migration.key):
                self.assertEqual(migration.postcheck_path, migration.livecheck_path)
                self.assertEqual(
                    migration.postcheck_checksum,
                    migration.livecheck_checksum,
                )
                self.assertEqual(migration.postcheck_bytes, migration.livecheck_bytes)

    def test_237_keeps_published_evidence_and_uses_stable_live_invariants(self):
        migration = next(item for item in self.migrations if item.order == 237)

        self.assertEqual(
            "d6a9098d43c5469438eb2a576c76db89bf059f162f3297069e5aea8a3c7ce87b",
            migration.checksum,
        )
        self.assertEqual(
            "f9af44735748508655bec943f5efa7fb33809c4d595b09463c284d19bf7f6348",
            migration.postcheck_checksum,
        )
        self.assertEqual(
            PurePosixPath(
                "db/livecheck/"
                "237_warehouse_forwarder_quote_and_transport_eligibility.sql"
            ),
            migration.livecheck_path,
        )
        self.assertNotEqual(migration.postcheck_checksum, migration.livecheck_checksum)

        livecheck = migration.livecheck_sql
        for mutable_snapshot_marker in (
            "YT-SAU-20260728",
            "YT-SAU-UNDATED-001",
            "SHA2(",
            "COUNT(*) FROM forwarder_quote_numeric_adjustment)=4",
            "COUNT(*) FROM forwarder_quote_numeric_adjustment_log)=4",
        ):
            self.assertNotIn(mutable_snapshot_marker, livecheck)
        for durable_marker in (
            "legacy numeric adjustment writer fenced by migration 237",
            "uk_fq_numeric_adjustment_current",
            "product_forwarder_transport_eligibility",
            "uk_pfte_active_scope",
            "chk_pfte_scope_codes",
            "product_forwarder_eligibility_scope_anchor",
            "chk_shipping_line_eligibility_snapshot",
            "product_management_id_sequence",
        ):
            self.assertIn(durable_marker, livecheck)


if __name__ == "__main__":
    unittest.main()
