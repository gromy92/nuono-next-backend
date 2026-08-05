from __future__ import annotations

import sys
import unittest
from pathlib import Path, PurePosixPath

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.core import (  # noqa: E402
    MigrationError,
    MigrationRunner,
    MigrationState,
)
from tests.schema_migration_fakes import FakeDatabase  # noqa: E402


class MigrationLivecheckContractTest(unittest.TestCase):
    SEPARATE_LIVECHECK_ORDERS = frozenset({237, 243})

    @classmethod
    def setUpClass(cls):
        cls.migrations = load_catalog(
            SCRIPT_DIR.parent / "src/main/resources"
        )

    def test_default_remains_as_strict_as_the_original_postcheck(self):
        for migration in self.migrations:
            if migration.order in self.SEPARATE_LIVECHECK_ORDERS:
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

    def test_243_uses_a_separate_additive_compatible_livecheck(self):
        migration = next(item for item in self.migrations if item.order == 243)

        self.assertEqual("AUTO_ADDITIVE", migration.kind)
        self.assertNotEqual(migration.postcheck_checksum, migration.livecheck_checksum)
        self.assertEqual(
            PurePosixPath("db/livecheck/243_dp_pull_runtime.sql"),
            migration.livecheck_path,
        )
        for marker in (
            "expected_column",
            "expected_index",
            "a.is_nullable='YES'",
            "a.non_unique=1 AND a.safe_shape=1",
            "dp_pull_runtime_additive_livecheck",
        ):
            self.assertIn(marker, migration.livecheck_sql)

    def test_completed_migration_uses_livecheck_not_one_time_postcheck(self):
        completed, pending = self.migrations[1:3]
        database = self.database_with_completed(completed)
        database.postcheck_results[completed.key] = False
        database.livecheck_results[completed.key] = True

        self.runner(database).apply()

        self.assertIn(("livecheck", completed.key), database.events)
        self.assertNotIn(("postcheck", completed.key), database.events)
        self.assertIn(("script", pending.key), database.events)
        self.assertIn(("postcheck", pending.key), database.events)

    def test_failed_livecheck_still_blocks_before_pending_script(self):
        completed, pending = self.migrations[1:3]
        database = self.database_with_completed(completed)
        database.postcheck_results[completed.key] = True
        database.livecheck_results[completed.key] = False

        with self.assertRaisesRegex(MigrationError, "live schema drift"):
            self.runner(database).apply()

        self.assertNotIn(("script", pending.key), database.events)

    @staticmethod
    def database_with_completed(migration):
        state = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "APPLIED",
            1,
        )
        return FakeDatabase({migration.key: state})

    def runner(self, database):
        return MigrationRunner(
            database,
            self.migrations[:3],
            release_commit="a" * 40,
            installed_by="unit-test",
        )


if __name__ == "__main__":
    unittest.main()
