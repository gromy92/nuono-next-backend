from __future__ import annotations

import hashlib
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.core import (  # noqa: E402
    MigrationError,
    MigrationRunner,
    MigrationState,
    plan_migrations,
)
from tests.schema_migration_fakes import FakeDatabase  # noqa: E402


class ReleaseSchemaMigrationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "db/init").mkdir(parents=True)
        (self.root / "db/postcheck").mkdir(parents=True)
        self.write_migration("227_history.sql", "BOOTSTRAP", "SELECT 227;\n")
        self.write_migration("228_feature.sql", "AUTO_ADDITIVE", "SELECT 228;\n")
        (self.root / "db/init/release-migrations.tsv").write_text(
            "order\tmigration_key\tkind\tscript_path\tpostcheck_path\n"
            "227\t227_history.sql\tBOOTSTRAP\tdb/init/227_history.sql\t"
            "db/postcheck/227_history.sql\n"
            "228\t228_feature.sql\tAUTO_ADDITIVE\tdb/init/228_feature.sql\t"
            "db/postcheck/228_feature.sql\n",
            encoding="utf-8",
        )
        self.migrations = load_catalog(self.root)

    def tearDown(self):
        self.temporary.cleanup()

    def write_migration(self, name, kind, sql):
        del kind
        (self.root / "db/init" / name).write_text(sql, encoding="utf-8")
        (self.root / "db/postcheck" / name).write_text("SELECT 1;\n", encoding="utf-8")

    def test_catalog_uses_exact_filename_identity_and_sha256(self):
        migration = self.migrations[1]

        self.assertEqual("228_feature.sql", migration.key)
        self.assertEqual(
            hashlib.sha256(b"SELECT 228;\n").hexdigest(),
            migration.checksum,
        )
        self.assertEqual([227, 228], [item.order for item in self.migrations])

    def test_catalog_must_start_at_227_and_have_no_order_gaps(self):
        cases = (
            (
                "wrong start",
                "228\t228_history.sql\tBOOTSTRAP\tdb/init/228_history.sql\t"
                "db/postcheck/228_history.sql\n",
            ),
            (
                "gap",
                "227\t227_history.sql\tBOOTSTRAP\tdb/init/227_history.sql\t"
                "db/postcheck/227_history.sql\n"
                "229\t229_feature.sql\tAUTO_ADDITIVE\tdb/init/229_feature.sql\t"
                "db/postcheck/229_feature.sql\n",
            ),
        )
        for label, rows in cases:
            with self.subTest(label=label):
                for order, name in (
                    (227, "227_history.sql"),
                    (228, "228_history.sql"),
                    (229, "229_feature.sql"),
                ):
                    self.write_migration(name, "ignored", f"SELECT {order};\n")
                (self.root / "db/init/release-migrations.tsv").write_text(
                    "order\tmigration_key\tkind\tscript_path\tpostcheck_path\n"
                    + rows,
                    encoding="utf-8",
                )
                with self.assertRaisesRegex(MigrationError, "start at 227|continuous"):
                    load_catalog(self.root)

    def test_applied_same_checksum_is_skipped_and_drift_fails_closed(self):
        migration = self.migrations[1]
        applied = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "APPLIED",
            1,
        )

        self.assertEqual([], plan_migrations([migration], {migration.key: applied}))
        drifted = replace(applied, checksum="0" * 64)
        with self.assertRaisesRegex(MigrationError, "checksum drift"):
            plan_migrations([migration], {migration.key: drifted})
        drifted_postcheck = replace(applied, postcheck_checksum="f" * 64)
        with self.assertRaisesRegex(MigrationError, "postcheck checksum drift"):
            plan_migrations([migration], {migration.key: drifted_postcheck})

    def test_apply_marks_applied_only_after_script_and_postcheck(self):
        database = FakeDatabase()
        runner = self.runner(database)

        runner.apply()

        self.assertEqual(
            [
                ("lock", 30),
                ("bootstrap", "227_history.sql"),
                ("postcheck", "227_history.sql"),
                ("begin", "228_feature.sql", 1, "APPLY"),
                ("script", "228_feature.sql"),
                ("postcheck", "228_feature.sql"),
                ("applied", "228_feature.sql", 1),
                ("unlock",),
            ],
            database.events,
        )

    def test_failure_is_recorded_and_blocks_automatic_replay(self):
        database = FakeDatabase()
        database.script_error = RuntimeError("ddl failed")
        runner = self.runner(database)

        with self.assertRaisesRegex(MigrationError, "228_feature.sql"):
            runner.apply()
        self.assertEqual("FAILED", database.states["228_feature.sql"].state)
        database.script_error = None
        with self.assertRaisesRegex(MigrationError, "repair-forward"):
            runner.apply()

    def test_repair_forward_postchecks_before_optional_rerun(self):
        migration = self.migrations[1]
        failed = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "FAILED",
            2,
        )
        database = FakeDatabase({migration.key: failed})
        runner = self.runner(database)

        runner.repair_forward(migration.key)

        self.assertNotIn(("script", migration.key), database.events)
        self.assertIn(("postcheck", migration.key), database.events)
        self.assertEqual("APPLIED", database.states[migration.key].state)
        self.assertEqual(3, database.states[migration.key].attempt_no)
        self.assertIn(
            ("reconciled", migration.key, 2, 3),
            database.events,
        )

    def test_repair_forward_requires_explicit_rerun_when_postcheck_fails(self):
        migration = self.migrations[1]
        failed = MigrationState(
            migration.key,
            migration.checksum,
            migration.postcheck_checksum,
            "APPLYING",
            1,
        )
        database = FakeDatabase({migration.key: failed})
        database.postcheck_results[migration.key] = False
        runner = self.runner(database)

        with self.assertRaisesRegex(MigrationError, "--rerun"):
            runner.repair_forward(migration.key)

    def test_managed_migration_requires_explicit_approval(self):
        managed = replace(self.migrations[1], kind="MANAGED")
        database = FakeDatabase()
        runner = MigrationRunner(
            database,
            (self.migrations[0], managed),
            release_commit="a" * 40,
            installed_by="unit-test",
        )

        with self.assertRaisesRegex(MigrationError, "approvals must cover pending"):
            runner.apply()
        self.assertNotIn(("script", managed.key), database.events)
        with self.assertRaisesRegex(MigrationError, "not allowed"):
            runner.apply(
                approved_managed=[managed.key, self.migrations[0].key],
            )
        with self.assertRaisesRegex(MigrationError, "not allowed"):
            runner.apply(
                approved_managed=[managed.key, "209_unknown_managed.sql"],
            )

        runner.apply(approved_managed=[managed.key])
        self.assertIn(("applied", managed.key, 1), database.events)
        self.assertEqual([], runner.apply(approved_managed=[managed.key]))

        database.states[managed.key] = replace(
            database.states[managed.key],
            state="FAILED",
        )
        with self.assertRaisesRegex(MigrationError, "approvals must cover pending"):
            runner.repair_forward(managed.key)
        with self.assertRaisesRegex(MigrationError, "approvals must cover pending"):
            runner.repair_forward(
                managed.key,
                approved_managed=["209_wrong_managed.sql"],
            )
        runner.repair_forward(
            managed.key,
            approved_managed=[managed.key],
        )

    def test_history_must_be_a_continuous_applied_prefix(self):
        bootstrap, feature = self.migrations
        feature_state = MigrationState(
            feature.key,
            feature.checksum,
            feature.postcheck_checksum,
            "APPLIED",
            1,
        )

        with self.assertRaisesRegex(MigrationError, "continuous catalog prefix"):
            plan_migrations(self.migrations, {feature.key: feature_state})

    def test_only_the_first_bootstrap_may_be_baselined(self):
        feature = self.migrations[1]
        invalid = MigrationState(
            feature.key,
            feature.checksum,
            feature.postcheck_checksum,
            "BASELINED",
            1,
        )

        with self.assertRaisesRegex(MigrationError, "only valid"):
            plan_migrations((feature,), {feature.key: invalid})

    def test_repair_rejects_a_missing_predecessor(self):
        feature = self.migrations[1]
        failed = MigrationState(
            feature.key,
            feature.checksum,
            feature.postcheck_checksum,
            "FAILED",
            1,
        )
        database = FakeDatabase({feature.key: failed})

        # A deliberately broken bootstrap adapter leaves the predecessor absent.
        database.bootstrap = lambda *args: database.events.append(("bootstrap",))
        with self.assertRaisesRegex(MigrationError, "predecessor is missing"):
            self.runner(database).repair_forward(feature.key)

    def runner(self, database):
        return MigrationRunner(
            database,
            self.migrations,
            release_commit="a" * 40,
            installed_by="unit-test",
            lock_timeout_seconds=30,
        )


if __name__ == "__main__":
    unittest.main()
